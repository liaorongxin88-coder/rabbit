package com.rabbit.app.ui;

import android.os.Bundle;
import android.view.View;
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

public class RecordListActivity extends AppCompatActivity {
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private TwoLineCardAdapter adapter;

    private String kind;
    private String title;
    private long batchId;

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

        kind = getIntent() == null ? "" : getIntent().getStringExtra("kind");
        title = getIntent() == null ? "" : getIntent().getStringExtra("title");
        batchId = getIntent() == null ? 0L : getIntent().getLongExtra("batchId", 0L);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText(title == null || title.isEmpty() ? "记录" : title);
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.VISIBLE);
        btnTopRight.setText("刷新");
        btnTopRight.setOnClickListener(v -> load());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (batchId <= 0) {
            state.showError("batchId缺失", "返回", v -> finish());
            return;
        }
        String path = buildPath();
        if (path == null) {
            state.showError("不支持的记录类型：" + kind, "返回", v -> finish());
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson(path, token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> rows = buildRows(arr);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    adapter.clear();
                    adapter.addAll(rows);
                    adapter.notifyDataSetChanged();
                    if (rows.isEmpty()) {
                        state.showEmpty("暂无记录", "批次ID=" + batchId, "刷新", v -> load());
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

    private String buildPath() {
        String k = kind == null ? "" : kind.trim().toUpperCase();
        if ("PREPARTUM".equals(k)) {
            return "/api/prepartum-records?batchId=" + batchId;
        }
        if ("PREG_CHECK".equals(k)) {
            return "/api/pregnancy-check-records?batchId=" + batchId + "&limit=200";
        }
        if ("PARTURITION".equals(k)) {
            return "/api/parturition-records?batchId=" + batchId + "&limit=200";
        }
        if ("WEANING".equals(k)) {
            return "/api/weaning-records?batchId=" + batchId + "&limit=200";
        }
        return null;
    }

    private List<String> buildRows(JsonArray arr) {
        List<String> rows = new ArrayList<String>();
        String k = kind == null ? "" : kind.trim().toUpperCase();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            if ("PREPARTUM".equals(k)) {
                String rabbitId = safeAny(o, "rabbitId");
                String dt = safeAny(o, "actionDate");
                String remark = safeStr(o, "remark");
                String title = "临产完成 " + TimeUtil.fmtAny(dt) + "||batch:临产";
                String sub = "母兔#" + rabbitId + (remark.isEmpty() ? "" : ("\n" + remark));
                rows.add(title + "\n" + sub);
            } else if ("PREG_CHECK".equals(k)) {
                String rabbitId = safeAny(o, "rabbitId");
                String dt = safeAny(o, "checkDate");
                String res = safeStr(o, "result");
                String remark = safeStr(o, "remark");
                String title = "摸胎 " + TimeUtil.fmtAny(dt) + "||batch:摸胎";
                String sub = "母兔#" + rabbitId + "  结果：" + res + (remark.isEmpty() ? "" : ("\n" + remark));
                rows.add(title + "\n" + sub);
            } else if ("PARTURITION".equals(k)) {
                String rabbitId = safeAny(o, "rabbitId");
                String dt = safeAny(o, "birthDate");
                String total = safeAny(o, "totalKits");
                String live = safeAny(o, "liveKits");
                String remark = safeStr(o, "remark");
                String title = "分娩 " + TimeUtil.fmtAny(dt) + "||batch:分娩";
                String sub = "母兔#" + rabbitId + "  总:" + total + "  活:" + live + (remark.isEmpty() ? "" : ("\n" + remark));
                rows.add(title + "\n" + sub);
            } else if ("WEANING".equals(k)) {
                String rabbitId = safeAny(o, "rabbitId");
                String dt = safeAny(o, "weaningDate");
                String cnt = safeAny(o, "weaningCount");
                String avg = safeAny(o, "avgWeight");
                String inCageId = safeAny(o, "inCageId");
                String inCageNumber = safeAny(o, "inCageNumber");
                String alloc = safeAny(o, "allocSummary");
                String targetCageId = safeAny(o, "targetCageId");
                String targetCageNumber = safeAny(o, "targetCageNumber");
                String remark = safeStr(o, "remark");
                String title = "断奶 " + TimeUtil.fmtAny(dt) + "||batch:断奶";
                String cageText = "";
                if (!inCageId.isEmpty()) {
                    cageText = "  入栏:" + (inCageNumber.isEmpty() ? ("#" + inCageId) : inCageNumber);
                } else if (!targetCageId.isEmpty()) {
                    cageText = "  目标:" + (targetCageNumber.isEmpty() ? ("#" + targetCageId) : targetCageNumber);
                }
                String allocText = alloc == null || alloc.isEmpty() ? "" : ("\n分笼:" + alloc);
                String sub = "母兔#" + rabbitId + "  数量:" + cnt + "  均重:" + (avg.isEmpty() ? "-" : avg) + cageText + allocText + (remark.isEmpty() ? "" : ("\n" + remark));
                rows.add(title + "\n" + sub);
            }
        }
        return rows;
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
