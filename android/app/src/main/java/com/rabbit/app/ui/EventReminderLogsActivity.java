package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class EventReminderLogsActivity extends AppCompatActivity {
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
        tvTopTitle.setText("提醒扫描日志");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            state.showEmpty("🔒", "未登录", "请先登录", "关闭", () -> finish());
            return;
        }
        if (houseId <= 0) {
            state.showEmpty("🏠", "未选择兔舍", "请选择兔舍后再查看日志", "关闭", () -> finish());
            return;
        }
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/event-reminder-logs?limit=200", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i).isJsonObject()) {
                        continue;
                    }
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsLong() : 0L;
                    String category = o.has("category") && !o.get("category").isJsonNull() ? o.get("category").getAsString() : "";
                    long recordId = o.has("recordId") && !o.get("recordId").isJsonNull() ? o.get("recordId").getAsLong() : 0L;
                    String eventDate = o.has("eventDate") && !o.get("eventDate").isJsonNull() ? TimeUtil.fmtAny(o.get("eventDate").getAsString()) : "";
                    String notifyDate = o.has("notifyDate") && !o.get("notifyDate").isJsonNull() ? TimeUtil.fmtAny(o.get("notifyDate").getAsString()) : "";
                    String notifyTime = o.has("notifyTime") && !o.get("notifyTime").isJsonNull() ? TimeUtil.fmtAny(o.get("notifyTime").getAsString()) : "";
                    String ct = o.has("createTime") && !o.get("createTime").isJsonNull() ? TimeUtil.fmtAny(o.get("createTime").getAsString()) : "";
                    String line1 = safe(category) + " · record#" + recordId + " · event:" + safe(eventDate);
                    String line2 = "notifyDate:" + safe(notifyDate) + " notifyTime:" + safe(notifyTime) + "\nlog#" + id + " create:" + safe(ct);
                    items.add(line1 + "\n" + line2);
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    if (items.isEmpty()) {
                        state.showEmpty("📄", "暂无日志", "没有提醒扫描日志", "刷新", this::load);
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::load);
                });
            }
        }).start();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}

