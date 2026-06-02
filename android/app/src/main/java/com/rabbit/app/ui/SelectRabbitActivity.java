package com.rabbit.app.ui;

import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;

public class SelectRabbitActivity extends AppCompatActivity {
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
    private final List<Long> rabbitIds = new ArrayList<Long>();

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
        tvTopTitle.setText("选择兔子");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.VISIBLE);
        btnTopRight.setText("刷新");
        btnTopRight.setOnClickListener(v -> load());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < rabbitIds.size()) {
                long rabbitId = rabbitIds.get(position);
                Intent it = new Intent(this, RabbitStatusHistoryActivity.class);
                it.putExtra("rabbitId", rabbitId);
                startActivity(it);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/rabbits?active=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                rabbitIds.clear();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.get("id").getAsLong();
                    rabbitIds.add(id);
                    String type = safeStr(o, "type");
                    String gender = safeStr(o, "gender");
                    String cageId = safeStr(o, "cageId");
                    items.add("兔#" + id + "  类型:" + type + "  性别:" + gender + "\n笼位#" + cageId + "（点开看状态历史）");
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    if (items.isEmpty()) {
                        state.showEmpty("🐇", "暂无兔子", "先去“兔子”页面录入，再回来选择", "刷新", this::load);
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::load);
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
