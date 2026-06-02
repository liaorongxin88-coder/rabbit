package com.rabbit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class BatchesActivity extends AppCompatActivity {
    private Button btnRefresh;
    private Button btnCreate;
    private ListView lv;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private ArrayAdapter<String> adapter;
    private final List<Long> batchIds = new ArrayList<Long>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batches);

        btnRefresh = findViewById(R.id.btnRefreshBatches);
        btnCreate = findViewById(R.id.btnCreateBatch);
        lv = findViewById(R.id.lvBatches);
        state = new StatePanel(this);

        api = new ApiClient();
        session = new SessionStore(this);
        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);

        tvTopTitle.setText("批次");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        btnRefresh.setOnClickListener(v -> load());
        btnCreate.setOnClickListener(v -> startActivity(new Intent(this, CreateBatchActivity.class)));

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= batchIds.size()) {
                return;
            }
            long batchId = batchIds.get(position);
            Intent it = new Intent(this, BatchDetailActivity.class);
            it.putExtra("batchId", batchId);
            startActivity(it);
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
        runOnUiThread(() -> state.hide());
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/batches", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                batchIds.clear();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.get("id").getAsLong();
                    String code = o.get("batchCode").getAsString();
                    String status = o.get("status").getAsString();
                    batchIds.add(id);
                    items.add("批次#" + id + "  " + code + "||batch:" + status + "\n状态：" + status + "（点开进入操作）");
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    if (items.isEmpty()) {
                        state.showEmpty("🧬", "还没有批次", "先创建一个批次，再按流程推进到配种/分娩/断奶", "创建批次", () -> startActivity(new Intent(this, CreateBatchActivity.class)));
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    state.showError(e.getMessage(), "重试", this::load);
                });
            }
        }).start();
    }
}
