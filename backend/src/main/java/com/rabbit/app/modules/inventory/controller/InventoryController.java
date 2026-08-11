package com.rabbit.app.modules.inventory.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.inventory.dto.CreateInventoryItemRequest;
import com.rabbit.app.modules.inventory.dto.CreateInventoryTxRequest;
import com.rabbit.app.modules.inventory.entity.InventoryItem;
import com.rabbit.app.modules.inventory.entity.InventoryTx;
import com.rabbit.app.modules.inventory.service.InventoryService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Validated
@RestController
@RequestMapping("/api/inventory")
@RequiresPermission(PermissionCode.RABBIT_INVENTORY_LIST)
public class InventoryController {
    private final HouseService houseService;
    private final InventoryService inventoryService;

    public InventoryController(HouseService houseService, InventoryService inventoryService) {
        this.houseService = houseService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/items")
    public ApiResponse<List<InventoryItem>> listItems(@RequestHeader("X-House-Id") Long houseId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(inventoryService.listItems(houseId));
    }

    @PostMapping("/items")
    @RequiresPermission(PermissionCode.RABBIT_INVENTORY_EDIT)
    public ApiResponse<InventoryItem> createItem(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody CreateInventoryItemRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        InventoryItem item = new InventoryItem();
        item.setName(req.getName());
        item.setUnit(req.getUnit());
        item.setLowStockQty(req.getLowStockQty());
        item.setRemark(req.getRemark());
        return ApiResponse.ok(inventoryService.createItem(userId, houseId, item, req.getInitQty(), req.getRequestId()));
    }

    @PostMapping("/txs")
    @RequiresPermission(PermissionCode.RABBIT_INVENTORY_EDIT)
    public ApiResponse<Void> addTx(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody CreateInventoryTxRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        inventoryService.addTx(userId, houseId, req.getItemId(), req.getTxType(), req.getQtyDelta(), req.getTxTime(), req.getRemark(), req.getRequestId(), null, null);
        return ApiResponse.ok(null);
    }

    @GetMapping("/txs")
    public ApiResponse<List<InventoryTx>> listTxs(@RequestHeader("X-House-Id") Long houseId,
                                                  @RequestParam("itemId") Long itemId,
                                                  @RequestParam(value = "page", required = false) Integer page,
                                                  @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(inventoryService.listTxByItem(houseId, itemId, page == null ? 1 : page, pageSize == null ? 50 : pageSize));
    }

    @GetMapping(value = "/items.csv")
    @RequiresPermission(PermissionCode.RABBIT_INVENTORY_EXPORT)
    public org.springframework.http.ResponseEntity<StreamingResponseBody> exportItemsCsv(@RequestHeader("X-House-Id") Long houseId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");

        StreamingResponseBody body = outputStream -> {
            writeUtf8Bom(outputStream);
            writeCsvLine(outputStream, "id,name,unit,current_qty,low_stock_qty,remark,update_time\n");
            List<InventoryItem> items = inventoryService.listItems(houseId);
            if (items != null) {
                for (InventoryItem it : items) {
                    String line = v(it.getId()) + ","
                            + csv(it.getName()) + ","
                            + csv(it.getUnit()) + ","
                            + csv(v(it.getCurrentQty())) + ","
                            + csv(v(it.getLowStockQty())) + ","
                            + csv(it.getRemark()) + ","
                            + v(it.getUpdateTime())
                            + "\n";
                    writeCsvLine(outputStream, line);
                }
            }
            outputStream.flush();
        };
        return org.springframework.http.ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventory_items.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping(value = "/txs.csv")
    @RequiresPermission(PermissionCode.RABBIT_INVENTORY_EXPORT)
    public org.springframework.http.ResponseEntity<StreamingResponseBody> exportTxsCsv(@RequestHeader("X-House-Id") Long houseId,
                                                                                      @RequestParam("itemId") Long itemId,
                                                                                      @RequestParam(value = "from", required = false) Long from,
                                                                                      @RequestParam(value = "to", required = false) Long to,
                                                                                      @RequestParam(value = "maxRows", required = false) Integer maxRows) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        Date fromDate = from == null ? null : new Date(from);
        Date toDate = to == null ? null : new Date(to);

        int limitRows = maxRows == null ? 50000 : maxRows.intValue();
        if (limitRows < 0) {
            limitRows = 50000;
        }
        if (limitRows > 500000) {
            limitRows = 500000;
        }
        final int pageSize = 1000;
        int finalLimitRows = limitRows;

        InventoryItem item = inventoryService.getItem(houseId, itemId);
        String itemName = item == null ? "" : item.getName();

        StreamingResponseBody body = outputStream -> {
            writeUtf8Bom(outputStream);
            writeCsvLine(outputStream, "item_id,item_name,id,tx_time,tx_type,qty_delta,ref_table,ref_id,remark,request_id,create_by\n");
            int offset = 0;
            int written = 0;
            while (true) {
                int limit = pageSize;
                if (finalLimitRows > 0 && written + limit > finalLimitRows) {
                    limit = finalLimitRows - written;
                }
                if (limit <= 0) {
                    break;
                }
                List<InventoryTx> part = inventoryService.listTxExportPage(houseId, itemId, fromDate, toDate, offset, limit);
                if (part == null || part.isEmpty()) {
                    break;
                }
                for (InventoryTx tx : part) {
                    String line = v(itemId) + ","
                            + csv(itemName) + ","
                            + v(tx.getId()) + ","
                            + v(tx.getTxTime()) + ","
                            + csv(tx.getTxType()) + ","
                            + csv(v(tx.getQtyDelta())) + ","
                            + csv(tx.getRefTable()) + ","
                            + v(tx.getRefId()) + ","
                            + csv(tx.getRemark()) + ","
                            + csv(tx.getRequestId()) + ","
                            + csv(tx.getCreateBy())
                            + "\n";
                    writeCsvLine(outputStream, line);
                    written++;
                    if (finalLimitRows > 0 && written >= finalLimitRows) {
                        break;
                    }
                }
                if (finalLimitRows > 0 && written >= finalLimitRows) {
                    break;
                }
                if (part.size() < limit) {
                    break;
                }
                offset += part.size();
            }
            outputStream.flush();
        };
        return org.springframework.http.ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventory_txs.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }

    private String v(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String csv(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\"", "\"\"");
        if (t.contains(",") || t.contains("\n") || t.contains("\r")) {
            return "\"" + t + "\"";
        }
        return t;
    }

    private void writeUtf8Bom(OutputStream os) throws IOException {
        os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
    }

    private void writeCsvLine(OutputStream os, String s) throws IOException {
        if (s == null) {
            return;
        }
        os.write(s.getBytes(StandardCharsets.UTF_8));
    }
}
