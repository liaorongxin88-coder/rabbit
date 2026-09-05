package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class BatchStatisticsExportIT extends E2eTestSupport {
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Test
    void exportsTheHouseScopedStatisticsSnapshotAsARealWorkbook() throws Exception {
        UserSession owner = register("batch_statistics_export");
        String houseName = "导出验收兔舍";
        String batchCode = "EXPORT-A";
        long houseId = createHouse(owner, houseName, 1, 1, 1);
        long batchId = createBatch(owner, houseId, batchCode);

        E2eApiClient.Download download = api.download(endpoint(batchId), owner.token, houseId);

        assertEquals(XLSX_MEDIA_TYPE, download.contentType.toString());
        assertTrue(download.contentDisposition.contains(
                "filename=\"batch-EXPORT-A-statistics-"
        ));
        assertTrue(download.contentDisposition.contains(
                "filename*=UTF-8''%E6%89%B9%E6%AC%A1-EXPORT-A-%E7%BB%9F%E8%AE%A1-"
        ));
        assertTrue(download.bytes.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(download.bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("批次统计", workbook.getSheetName(0));
            assertEquals("口径与状态", workbook.getSheetName(1));
            assertFalse(workbook.isSheetHidden(0));
            assertFalse(workbook.isSheetHidden(1));

            Sheet summary = workbook.getSheetAt(0);
            Row header = summary.getRow(0);
            Row values = summary.getRow(1);
            assertEquals(31, header.getLastCellNum());
            assertEquals("兔舍", header.getCell(0).getStringCellValue());
            assertEquals("批次编号", header.getCell(1).getStringCellValue());
            assertEquals("统计时间", header.getCell(2).getStringCellValue());
            assertEquals("配种母兔数", header.getCell(4).getStringCellValue());
            assertEquals("怀孕数量", header.getCell(7).getStringCellValue());
            assertEquals("出肉率", header.getCell(30).getStringCellValue());
            for (CellType type : headerCellTypes(header)) {
                assertEquals(CellType.STRING, type);
            }
            assertEquals(houseName, values.getCell(0).getStringCellValue());
            assertEquals(batchCode, values.getCell(1).getStringCellValue());
            assertEquals(CellType.NUMERIC, values.getCell(2).getCellType());
            assertEquals("未录入", values.getCell(3).getStringCellValue());
            assertEquals(CellType.NUMERIC, values.getCell(4).getCellType());
            assertEquals(0D, values.getCell(4).getNumericCellValue());
            assertEquals("暂无可计算数据", values.getCell(5).getStringCellValue());
            assertEquals("未录入", values.getCell(30).getStringCellValue());

            Sheet detail = workbook.getSheetAt(1);
            assertEquals(29, detail.getPhysicalNumberOfRows());
            assertEquals(28, detail.getLastRowNum());
            assertEquals(13, detail.getRow(0).getLastCellNum());
            assertEquals("MATING_DATE", detail.getRow(1).getCell(1).getStringCellValue());
            assertEquals("NOT_RECORDED", detail.getRow(1).getCell(7).getStringCellValue());
            assertEquals("MATED_DOE_COUNT", detail.getRow(2).getCell(1).getStringCellValue());
            assertEquals(0D, detail.getRow(2).getCell(5).getNumericCellValue());
            assertEquals("AVAILABLE", detail.getRow(2).getCell(7).getStringCellValue());
            assertEquals("CONCEPTION_RATE", detail.getRow(3).getCell(1).getStringCellValue());
            assertEquals("NOT_APPLICABLE", detail.getRow(3).getCell(7).getStringCellValue());
            assertEquals("CARCASS_YIELD_RATE", detail.getRow(28).getCell(1).getStringCellValue());
            assertEquals("NOT_RECORDED", detail.getRow(28).getCell(7).getStringCellValue());
        }
    }

    @Test
    void rejectsMissingHousePermissionAndCrossHouseBatchAccess() {
        UserSession owner = register("batch_statistics_export_owner");
        UserSession viewer = register("batch_statistics_export_viewer");
        UserSession outsider = register("batch_statistics_export_outsider");
        long sourceHouseId = createHouse(owner, "导出来源兔舍", 1, 1, 1);
        long otherHouseId = createHouse(owner, "导出目标兔舍", 1, 1, 1);
        long batchId = createBatch(owner, sourceHouseId, "EXPORT-ISOLATED");
        api.postOk("/api/house-members", owner.token, sourceHouseId, obj(
                "userName", viewer.userName,
                "role", "VIEWER",
                "requestId", requestId("batch_statistics_export_viewer")
        ));
        JsonNode viewerPermission = api.getOk(
                "/api/houses/permission",
                viewer.token,
                sourceHouseId
        );
        assertTrue(java.util.stream.StreamSupport.stream(
                viewerPermission.get("permissions").spliterator(),
                false
        ).anyMatch(permission -> "rabbit:reports:export".equals(permission.asText())));
        assertEquals(
                XLSX_MEDIA_TYPE,
                api.download(endpoint(batchId), viewer.token, sourceHouseId).contentType.toString()
        );

        api.expectError(
                endpoint(batchId),
                HttpMethod.GET,
                owner.token,
                null,
                null,
                400,
                "缺少X-House-Id"
        );
        api.expectError(
                endpoint(batchId),
                HttpMethod.GET,
                outsider.token,
                sourceHouseId,
                null,
                403,
                "无兔场权限"
        );
        api.expectError(
                endpoint(batchId),
                HttpMethod.GET,
                owner.token,
                otherHouseId,
                null,
                404,
                "批次不存在"
        );
    }

    private long createBatch(UserSession owner, long houseId, String batchCode) {
        return api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", batchCode,
                "femaleRabbitIds", java.util.List.of(),
                "requestId", requestId("batch_statistics_export")
        )).get("id").asLong();
    }

    private String endpoint(long batchId) {
        return "/api/reports/batches/" + batchId + "/statistics.xlsx";
    }

    private CellType[] headerCellTypes(Row header) {
        CellType[] types = new CellType[header.getLastCellNum()];
        for (int index = 0; index < types.length; index++) {
            types[index] = header.getCell(index).getCellType();
        }
        return types;
    }
}
