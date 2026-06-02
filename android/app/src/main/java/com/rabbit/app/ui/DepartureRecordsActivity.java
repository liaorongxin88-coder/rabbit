package com.rabbit.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
import com.rabbit.app.storage.RecentStore;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class DepartureRecordsActivity extends AppCompatActivity {
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private RecentStore recentStore;
    private TwoLineCardAdapter adapter;

    private long selectedRabbitId;
    private final List<PickerDialogUtil.PickItem> rabbitItems = new ArrayList<PickerDialogUtil.PickItem>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        lv = findViewById(R.id.lvList);
        pb = findViewById(R.id.pbListLoading);
        state = new StatePanel(this);

        api = new ApiClient();
        session = new SessionStore(this);
        recentStore = new RecentStore(this);
        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("离场记录");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.VISIBLE);
        btnTopRight.setText("操作");
        btnTopRight.setOnClickListener(v -> showActions());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void showActions() {
        String[] items = new String[]{"选择兔子筛选", "清除筛选", "刷新"};
        new AlertDialog.Builder(this)
                .setTitle("离场记录")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        pickRabbit();
                    } else if (which == 1) {
                        selectedRabbitId = 0L;
                        load();
                    } else if (which == 2) {
                        load();
                    }
                })
                .show();
    }

    private void pickRabbit() {
        if (!rabbitItems.isEmpty()) {
            showPickRabbitDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/rabbits", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                    if (id <= 0) {
                        continue;
                    }
                    String type = safeStr(o, "type");
                    String cageId = safeAny(o, "cageId");
                    String active = o.has("active") && !o.get("active").isJsonNull() ? (o.get("active").getAsBoolean() ? "在栏" : "离场") : "";
                    String label = "兔#" + id + "  " + active + "\n类型:" + type + "  笼位#" + cageId;
                    items.add(new PickerDialogUtil.PickItem(id, label, label));
                }
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    rabbitItems.clear();
                    rabbitItems.addAll(items);
                    showPickRabbitDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    state.showError("加载失败", e.getMessage(), "重试", v -> pickRabbit());
                });
            }
        }).start();
    }

    private void showPickRabbitDialog() {
        List<Long> recent = recentStore.getIds("departure_rabbit_" + session.getHouseId());
        PickerDialogUtil.showSingle(this, "选择兔子", rabbitItems, recent, it -> {
            if (it == null) {
                return;
            }
            selectedRabbitId = it.id;
            recentStore.push("departure_rabbit_" + session.getHouseId(), it.id, 50);
            load();
        });
    }

    private void load() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        String title = selectedRabbitId > 0 ? ("离场记录 兔#" + selectedRabbitId) : "离场记录";
        tvTopTitle.setText(title);
        new Thread(() -> {
            try {
                String url = "/api/departure-records?page=1&pageSize=200";
                if (selectedRabbitId > 0) {
                    url = url + "&rabbitId=" + selectedRabbitId;
                }
                JsonObject resp = api.getJson(url, token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> rows = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    String rabbitId = safeAny(o, "rabbitId");
                    String dt = safeAny(o, "departureDate");
                    String type = safeStr(o, "departureType");
                    String reason = safeStr(o, "reason");
                    String remark = safeStr(o, "remark");
                    String tag = "status:" + (type == null ? "" : type);
                    String t = "离场 " + TimeUtil.fmtAny(dt) + "||batch:离场 " + tag;
                    String sub = "兔#" + rabbitId + "  类型:" + type + (reason.isEmpty() ? "" : ("\n原因：" + reason)) + (remark.isEmpty() ? "" : ("\n" + remark));
                    rows.add(t + "\n" + sub);
                }
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    adapter.clear();
                    adapter.addAll(rows);
                    adapter.notifyDataSetChanged();
                    if (rows.isEmpty()) {
                        state.showEmpty("暂无记录", "可通过“死亡/淘汰/隔离”或“销售出栏”产生离场记录", "刷新", v -> load());
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    state.showError("加载失败", e.getMessage(), "重试", v -> load());
                });
            }
        }).start();
    }

    private String safeStr(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private String safeAny(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        try {
            return o.get(key).getAsString();
        } catch (Exception ignored) {
            try {
                return String.valueOf(o.get(key).getAsLong());
            } catch (Exception e) {
                return "";
            }
        }
    }
}

