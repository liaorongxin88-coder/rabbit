package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.util.TimeUtil;
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class ReplacementRecordsActivity extends AppCompatActivity {
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private ArrayAdapter<String> adapter;

    private final List<Long> recordIds = new ArrayList<Long>();
    private final List<Boolean> notified = new ArrayList<Boolean>();
    private boolean onlyUnnotified = true;
    private boolean acting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        lv = findViewById(R.id.lvList);
        pb = findViewById(R.id.pbListLoading);
        state = new StatePanel(this);

        api = new ApiClient();
        session = new SessionStore(this);
        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("后备成熟记录");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.VISIBLE);
        syncTopRight();
        btnTopRight.setOnClickListener(v -> {
            onlyUnnotified = !onlyUnnotified;
            syncTopRight();
            load();
        });
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= recordIds.size()) {
                return;
            }
            long rid = recordIds.get(position);
            boolean isNotified = position < notified.size() && Boolean.TRUE.equals(notified.get(position));
            if (rid <= 0) {
                return;
            }
            if (isNotified) {
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("操作")
                    .setItems(new String[]{"标记已提醒"}, (d, which) -> {
                        if (which == 0) {
                            markNotified(rid);
                        }
                    })
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void syncTopRight() {
        btnTopRight.setText(onlyUnnotified ? "全部" : "未提醒");
    }

    private void load() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            state.showEmpty("🔒", "未登录", "请先登录", "关闭", () -> finish());
            return;
        }
        if (houseId <= 0) {
            state.showEmpty("🏠", "未选择兔舍", "请选择兔舍后再查看记录", "关闭", () -> finish());
            return;
        }
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                String path = "/api/replacement-records?page=1&pageSize=200" + (onlyUnnotified ? "&matureNotified=false" : "");
                JsonObject resp = api.getJson(path, token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                recordIds.clear();
                notified.clear();
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i).isJsonObject()) {
                        continue;
                    }
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsLong() : 0L;
                    long rabbitId = o.has("rabbitId") && !o.get("rabbitId").isJsonNull() ? o.get("rabbitId").getAsLong() : 0L;
                    boolean isNotified = o.has("isMatureNotified") && !o.get("isMatureNotified").isJsonNull() && o.get("isMatureNotified").getAsBoolean();
                    String exp = o.has("expectedMatureDate") && !o.get("expectedMatureDate").isJsonNull() ? TimeUtil.fmtAny(o.get("expectedMatureDate").getAsString()) : "";
                    String nt = o.has("matureNotifyDate") && !o.get("matureNotifyDate").isJsonNull() ? TimeUtil.fmtAny(o.get("matureNotifyDate").getAsString()) : "";
                    recordIds.add(id);
                    notified.add(isNotified);
                    String line1 = "兔#" + rabbitId + " · " + (isNotified ? "已提醒" : "未提醒") + " · 到期:" + safe(exp);
                    String line2 = isNotified ? ("提醒时间:" + safe(nt) + "\n记录#" + id) : ("记录#" + id + "（点一下可标记已提醒）");
                    items.add(line1 + "\n" + line2);
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    if (items.isEmpty()) {
                        state.showEmpty("📄", "暂无记录", onlyUnnotified ? "没有未提醒的后备成熟记录" : "没有后备成熟记录", "刷新", () -> load());
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", () -> load());
                });
            }
        }).start();
    }

    private void markNotified(long recordId) {
        if (acting) {
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty() || houseId <= 0) {
            return;
        }
        acting = true;
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                JsonArray ids = new JsonArray();
                ids.add(recordId);
                body.add("recordIds", ids);
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                api.postJson("/api/replacement-records/mark-notified", token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    load();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    state.showError(e.getMessage(), "重试", () -> markNotified(recordId));
                });
            }
        }).start();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
