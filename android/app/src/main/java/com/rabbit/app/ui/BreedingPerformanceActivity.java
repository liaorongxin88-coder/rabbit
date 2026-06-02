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

public class BreedingPerformanceActivity extends AppCompatActivity {
    private ListView lv;
    private Button btnRefresh;
    private ProgressBar pb;
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
        setContentView(R.layout.activity_breeding_performance);

        lv = findViewById(R.id.lvBreedingPerformance);
        btnRefresh = findViewById(R.id.btnRefreshBreedingPerformance);
        pb = findViewById(R.id.pbBreedingLoading);

        api = new ApiClient();
        session = new SessionStore(this);
        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("繁殖性能");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        btnRefresh.setOnClickListener(v -> load());
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
        runOnUiThread(() -> pb.setVisibility(android.view.View.VISIBLE));
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/breeding-performance", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                rabbitIds.clear();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long rabbitId = o.get("rabbitId").getAsLong();
                    rabbitIds.add(rabbitId);
                    String line1 = "母兔#" + rabbitId + "  评分:" + safeNum(o, "performanceScore");
                    String line2 = "窝数:" + safeNum(o, "totalLitters")
                            + "  产仔:" + safeNum(o, "totalKits")
                            + "  断奶:" + safeNum(o, "totalWeaned");
                    String line = line1 + "\n" + line2;
                    items.add(line);
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.add(e.getMessage());
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                });
            }
        }).start();
    }

    private String safeNum(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        return o.get(key).getAsString();
    }
}
