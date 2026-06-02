package com.rabbit.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class InventoryActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private TextView tvResult;
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;

    private ApiClient api;
    private SessionStore session;
    private TwoLineCardAdapter adapter;

    private final List<Long> itemIds = new ArrayList<Long>();
    private final List<String> itemNames = new ArrayList<String>();
    private final List<String> itemUnits = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        api = new ApiClient();
        session = new SessionStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("库存管理");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.VISIBLE);
        btnTopRight.setText("新增");
        btnTopRight.setOnClickListener(v -> showAddItemDialog());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        tvResult = findViewById(R.id.tvInventoryResult);
        lv = findViewById(R.id.lvInventoryItems);
        pb = findViewById(R.id.pbInventoryLoading);
        state = new StatePanel(this);

        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= itemIds.size()) {
                return;
            }
            long itemId = itemIds.get(position);
            String name = position < itemNames.size() ? itemNames.get(position) : "";
            String unit = position < itemUnits.size() ? itemUnits.get(position) : "";
            showItemActions(itemId, name, unit);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItems();
    }

    private void loadItems() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/inventory/items", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> lines = new ArrayList<String>();
                List<Long> ids = new ArrayList<Long>();
                List<String> names = new ArrayList<String>();
                List<String> units = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                    String name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : "";
                    String unit = o.has("unit") && !o.get("unit").isJsonNull() ? o.get("unit").getAsString() : "";
                    String qty = o.has("currentQty") && !o.get("currentQty").isJsonNull() ? o.get("currentQty").getAsString() : "0";
                    String low = o.has("lowStockQty") && !o.get("lowStockQty").isJsonNull() ? o.get("lowStockQty").getAsString() : "";
                    if (id <= 0) {
                        continue;
                    }
                    ids.add(id);
                    names.add(name);
                    units.add(unit);
                    String line2 = "库存:" + qty + unit;
                    if (!low.isEmpty()) {
                        line2 = line2 + "  低库存:" + low + unit;
                    }
                    String tag = "inventory:库存";
                    Double qv = parseDouble(qty);
                    Double lv = low.isEmpty() ? null : parseDouble(low);
                    if (qv != null && lv != null && lv > 0 && qv <= lv) {
                        tag = "inventory!warning:低库存";
                    }
                    lines.add(name + "||" + tag + "\n" + line2);
                }
                runOnUiThread(() -> {
                    itemIds.clear();
                    itemNames.clear();
                    itemUnits.clear();
                    itemIds.addAll(ids);
                    itemNames.addAll(names);
                    itemUnits.addAll(units);
                    adapter.clear();
                    adapter.addAll(lines);
                    adapter.notifyDataSetChanged();
                    tvResult.setText("");
                    pb.setVisibility(View.GONE);
                    if (lines.isEmpty()) {
                        state.showEmpty("📦", "暂无物料", "右上角新增物料，投喂时可以直接选择并自动扣库存", "新增物料", this::showAddItemDialog);
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    tvResult.setText("");
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    state.showError(e.getMessage(), "重试", this::loadItems);
                });
            }
        }).start();
    }

    private void showAddItemDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_inventory_item, null, false);
        EditText etName = v.findViewById(R.id.etInvName);
        EditText etUnit = v.findViewById(R.id.etInvUnit);
        EditText etInit = v.findViewById(R.id.etInvInitQty);
        EditText etLow = v.findViewById(R.id.etInvLowStock);
        EditText etRemark = v.findViewById(R.id.etInvRemark);
        new AlertDialog.Builder(this)
                .setTitle("新增物料")
                .setView(v)
                .setPositiveButton("保存", (d, which) -> {
                    String name = etName.getText().toString().trim();
                    String unit = etUnit.getText().toString().trim();
                    String initQty = etInit.getText().toString().trim();
                    String lowQty = etLow.getText().toString().trim();
                    String remark = etRemark.getText().toString().trim();
                    if (name.isEmpty() || unit.isEmpty() || initQty.isEmpty()) {
                        Toast.makeText(this, "name/unit/initQty 必填", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Double init = parseDouble(initQty);
                    if (init == null) {
                        Toast.makeText(this, "initQty格式错误", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JsonObject body = new JsonObject();
                    body.addProperty("name", name);
                    body.addProperty("unit", unit);
                    body.addProperty("initQty", init);
                    if (!lowQty.isEmpty()) {
                        Double low = parseDouble(lowQty);
                        if (low == null) {
                            Toast.makeText(this, "lowStockQty格式错误", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        body.addProperty("lowStockQty", low);
                    }
                    if (!remark.isEmpty()) {
                        body.addProperty("remark", remark);
                    }
                    body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                    postJson("/api/inventory/items", body, () -> {
                        Toast.makeText(this, "已新增", Toast.LENGTH_SHORT).show();
                        loadItems();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showItemActions(long itemId, String name, String unit) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"入库/出库/调整", "查看流水"}, (d, which) -> {
                    if (which == 0) {
                        showTxDialog(itemId, unit);
                        return;
                    }
                    if (which == 1) {
                        showTxListDialog(itemId, name);
                    }
                })
                .show();
    }

    private void showTxDialog(long itemId, String unit) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_inventory_tx, null, false);
        EditText etDelta = v.findViewById(R.id.etInvDelta);
        EditText etRemark = v.findViewById(R.id.etInvTxRemark);
        new AlertDialog.Builder(this)
                .setTitle("库存变更（单位:" + unit + "）")
                .setView(v)
                .setPositiveButton("提交", (d, which) -> {
                    String delta = etDelta.getText().toString().trim();
                    String remark = etRemark.getText().toString().trim();
                    if (delta.isEmpty()) {
                        Toast.makeText(this, "请填写数量变更", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Double dv = parseDouble(delta);
                    if (dv == null || dv == 0) {
                        Toast.makeText(this, "数量变更格式错误", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JsonObject body = new JsonObject();
                    body.addProperty("itemId", itemId);
                    body.addProperty("txType", "ADJUST");
                    body.addProperty("qtyDelta", dv);
                    body.addProperty("txTime", System.currentTimeMillis());
                    if (!remark.isEmpty()) {
                        body.addProperty("remark", remark);
                    }
                    body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                    postJson("/api/inventory/txs", body, () -> {
                        Toast.makeText(this, "已提交", Toast.LENGTH_SHORT).show();
                        loadItems();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTxListDialog(long itemId, String name) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            state.showEmpty("🔒", "未登录", "请先登录后再查看库存流水", "去登录", () -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再查看库存流水", "关闭", this::finish);
            return;
        }
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/inventory/txs?itemId=" + itemId + "&page=1&pageSize=50", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> lines = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    String time = o.has("txTime") && !o.get("txTime").isJsonNull() ? TimeUtil.fmtAny(o.get("txTime").getAsString()) : "";
                    String type = o.has("txType") && !o.get("txType").isJsonNull() ? o.get("txType").getAsString() : "";
                    String delta = o.has("qtyDelta") && !o.get("qtyDelta").isJsonNull() ? o.get("qtyDelta").getAsString() : "";
                    String remark = o.has("remark") && !o.get("remark").isJsonNull() ? o.get("remark").getAsString() : "";
                    lines.add(time + "  " + type + "  " + delta + "\n" + remark);
                }
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    new AlertDialog.Builder(this)
                            .setTitle(name + " 流水")
                            .setAdapter(new TwoLineCardAdapter(this, lines), null)
                            .setPositiveButton("关闭", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    state.showError(e.getMessage(), "重试", () -> showTxListDialog(itemId, name));
                });
            }
        }).start();
    }

    private void postJson(String path, JsonObject body, Runnable onOk) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            state.showEmpty("🔒", "未登录", "请先登录后再提交", "去登录", () -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再提交", "关闭", this::finish);
            return;
        }
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                api.postJson(path, token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    if (onOk != null) {
                        onOk.run();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", () -> postJson(path, body, onOk));
                });
            }
        }).start();
    }

    private Double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return null;
        }
    }
}
