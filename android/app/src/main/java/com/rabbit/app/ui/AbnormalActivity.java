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

import java.util.ArrayList;
import java.util.List;

public class AbnormalActivity extends AppCompatActivity {
    private Button btnAll;
    private Button btnUndeal;
    private Button btnDealed;
    private ListView lv;
    private ProgressBar pb;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private ArrayAdapter<String> adapter;
    private StatePanel state;
    private boolean loading;

    private Boolean filterIsDeal = null;
    private final List<Long> ids = new ArrayList<Long>();
    private final List<Boolean> deals = new ArrayList<Boolean>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_abnormal);

        btnAll = findViewById(R.id.btnAbAll);
        btnUndeal = findViewById(R.id.btnAbUndeal);
        btnDealed = findViewById(R.id.btnAbDealed);
        lv = findViewById(R.id.lvAbnormal);
        pb = findViewById(R.id.pbAbnormalLoading);

        api = new ApiClient();
        session = new SessionStore(this);
        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);
        state = new StatePanel(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);

        tvTopTitle.setText("异常");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId() + "  ·  长按切换处理");
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.VISIBLE);
        btnTopRight.setText("刷新");
        btnTopRight.setOnClickListener(v -> load());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        btnAll.setOnClickListener(v -> {
            filterIsDeal = null;
            load();
        });
        btnUndeal.setOnClickListener(v -> {
            filterIsDeal = Boolean.FALSE;
            load();
        });
        btnDealed.setOnClickListener(v -> {
            filterIsDeal = Boolean.TRUE;
            load();
        });

        lv.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= ids.size()) {
                return true;
            }
            toggleDeal(ids.get(position), deals.get(position) == null ? false : deals.get(position));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (loading) {
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再查看异常", "刷新", this::load);
            return;
        }
        loading = true;
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                String path = "/api/abnormal";
                if (filterIsDeal != null) {
                    path += "?isDeal=" + (filterIsDeal ? "true" : "false");
                }
                JsonObject resp = api.getJson(path, token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                ids.clear();
                deals.clear();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.get("id").getAsLong();
                    String status = o.has("warningStatus") && !o.get("warningStatus").isJsonNull() ? o.get("warningStatus").getAsString() : "";
                    boolean isDeal = o.has("isDeal") && !o.get("isDeal").isJsonNull() && o.get("isDeal").getAsBoolean();
                    long rabbitId = o.has("rabbitId") ? o.get("rabbitId").getAsLong() : 0L;
                    ids.add(id);
                    deals.add(isDeal);
                    String line1 = (isDeal ? "已处理" : "未处理") + " · " + safe(status);
                    String line2 = "记录#" + id + "  兔#" + rabbitId;
                    items.add(line1 + "\n" + line2);
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    if (items.isEmpty()) {
                        state.showEmpty("⚠", "暂无异常", "当前筛选条件下没有异常记录", "刷新", this::load);
                    } else {
                        state.hide();
                    }
                    loading = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::load);
                    loading = false;
                });
            }
        }).start();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void toggleDeal(long id, boolean current) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("deal", !current);
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                api.postJson("/api/abnormal/" + id + "/deal", token, houseId, body);
                runOnUiThread(this::load);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    state.showError(e.getMessage(), "重试", () -> toggleDeal(id, current));
                });
            }
        }).start();
    }
}
