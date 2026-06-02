package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
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

public class RabbitStatusHistoryActivity extends AppCompatActivity {
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private android.widget.Button btnTopBack;
    private android.widget.Button btnTopRight;

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
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.VISIBLE);
        btnTopRight.setText("刷新");
        btnTopRight.setOnClickListener(v -> {
            long rabbitId = getIntent().getLongExtra("rabbitId", 0);
            if (rabbitId > 0) {
                load(rabbitId);
            }
        });
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);
    }

    @Override
    protected void onResume() {
        super.onResume();
        long rabbitId = getIntent().getLongExtra("rabbitId", 0);
        tvTopTitle.setText("状态历史");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId() + "  ·  兔#" + rabbitId);
        if (rabbitId > 0) {
            load(rabbitId);
        } else {
            adapter.clear();
            adapter.notifyDataSetChanged();
            state.showError("缺少rabbitId", null, null);
        }
    }

    private void load(long rabbitId) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/rabbit-status-history?rabbitId=" + rabbitId, token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    String t = TimeUtil.fmtAny(safeStr(o, "changeTime"));
                    String from = safeStr(o, "fromStatus");
                    String to = safeStr(o, "toStatus");
                    String reason = safeStr(o, "reason");
                    items.add(t + "  " + from + "→" + to + "\n原因：" + reason);
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    if (items.isEmpty()) {
                        state.showEmpty("📜", "暂无状态历史", "这只兔子还没有状态变更记录", "刷新", () -> load(rabbitId));
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", () -> load(rabbitId));
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
}
