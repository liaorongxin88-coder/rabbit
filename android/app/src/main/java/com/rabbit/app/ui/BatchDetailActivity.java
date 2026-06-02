package com.rabbit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

public class BatchDetailActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private TextView tvDetail;
    private Button btnOps;
    private Button btnRecords;
    private ProgressBar pb;
    private StatePanel state;

    private ApiClient api;
    private SessionStore session;

    private long batchId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_detail);

        api = new ApiClient();
        session = new SessionStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);

        tvDetail = findViewById(R.id.tvBatchDetail);
        btnOps = findViewById(R.id.btnBatchOps);
        btnRecords = findViewById(R.id.btnBatchRecords);
        pb = findViewById(R.id.pbBatchDetailLoading);
        state = new StatePanel(this);

        batchId = getIntent() == null ? 0L : getIntent().getLongExtra("batchId", 0L);
        tvTopTitle.setText("批次详情");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());

        btnOps.setOnClickListener(v -> {
            Intent it = new Intent(this, BatchOpsActivity.class);
            it.putExtra("batchId", batchId);
            startActivity(it);
        });
        btnRecords.setOnClickListener(v -> showRecordActions());
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
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/batches/" + batchId, token, houseId);
                JsonObject o = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : null;
                if (o == null) {
                    throw new RuntimeException("empty data");
                }
                String code = safeStr(o, "batchCode");
                String status = safeStr(o, "status");
                String start = safeAny(o, "startDate");
                String end = safeAny(o, "endDate");
                String remark = safeStr(o, "remark");
                String s = "批次ID：" + batchId
                        + "\n批次号：" + code
                        + "\n状态：" + status
                        + "\n开始：" + TimeUtil.fmtAny(start)
                        + "\n结束：" + TimeUtil.fmtAny(end)
                        + (remark.isEmpty() ? "" : ("\n备注：" + remark));
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    tvTopTitle.setText("批次 " + (code.isEmpty() ? String.valueOf(batchId) : code));
                    tvDetail.setText(s);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    state.showError("加载失败", e.getMessage(), "重试", v -> load());
                });
            }
        }).start();
    }

    private void showRecordActions() {
        String[] items = new String[]{"临产记录", "摸胎记录", "分娩记录", "断奶记录"};
        new AlertDialog.Builder(this)
                .setTitle("查看批次记录")
                .setItems(items, (d, which) -> {
                    String kind;
                    String title;
                    if (which == 0) {
                        kind = "PREPARTUM";
                        title = "临产记录";
                    } else if (which == 1) {
                        kind = "PREG_CHECK";
                        title = "摸胎记录";
                    } else if (which == 2) {
                        kind = "PARTURITION";
                        title = "分娩记录";
                    } else {
                        kind = "WEANING";
                        title = "断奶记录";
                    }
                    Intent it = new Intent(this, RecordListActivity.class);
                    it.putExtra("kind", kind);
                    it.putExtra("title", title);
                    it.putExtra("batchId", batchId);
                    startActivity(it);
                })
                .show();
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

