package com.rabbit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

public class CageSummaryActivity extends AppCompatActivity {
    private TextView tvMain;
    private TextView tvInfo;
    private TextView tvResult;
    private ProgressBar pb;
    private StatePanel state;
    private Button btnEnter;
    private Button btnFeed;
    private Button btnAbnormal;
    private Button btnClose;

    private ApiClient api;
    private SessionStore session;

    private long cageId;
    private String cageNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cage_summary);

        api = new ApiClient();
        session = new SessionStore(this);

        tvMain = findViewById(R.id.tvCageSummaryMain);
        tvInfo = findViewById(R.id.tvCageSummaryInfo);
        tvResult = findViewById(R.id.tvCageSummaryResult);
        pb = findViewById(R.id.pbCageSummary);
        state = new StatePanel(this);
        btnEnter = findViewById(R.id.btnCageSummaryEnter);
        btnFeed = findViewById(R.id.btnCageSummaryFeed);
        btnAbnormal = findViewById(R.id.btnCageSummaryAbnormal);
        btnClose = findViewById(R.id.btnCageSummaryClose);

        cageId = getIntent().getLongExtra("cageId", 0L);
        cageNumber = getIntent().getStringExtra("cageNumber");
        tvMain.setText("笼位：" + (cageNumber == null ? "-" : cageNumber) + "  (id=" + cageId + ")");

        btnClose.setOnClickListener(v -> finish());
        findViewById(R.id.cardCageSummary).setOnClickListener(v -> {});
        findViewById(android.R.id.content).setOnClickListener(v -> finish());

        btnEnter.setOnClickListener(v -> {
            Intent it = new Intent(this, RabbitsActivity.class);
            it.putExtra("cageId", cageId);
            if (cageNumber != null) {
                it.putExtra("cageNumber", cageNumber);
            }
            startActivity(it);
            finish();
        });
        btnFeed.setOnClickListener(v -> {
            startActivity(new Intent(this, FeedLogsActivity.class));
            finish();
        });
        btnAbnormal.setOnClickListener(v -> {
            startActivity(new Intent(this, AbnormalActivity.class));
            finish();
        });

        load();
    }

    private void load() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再使用", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "请先选择兔舍后再查看笼位", null, null);
            return;
        }
        if (cageId <= 0) {
            tvResult.setText("");
            state.showError("cageId缺失", "关闭", this::finish);
            return;
        }
        pb.setVisibility(View.VISIBLE);
        tvResult.setText("");
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/cages/" + cageId + "/summary", token, houseId);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : null;
                if (data == null) {
                    throw new RuntimeException("empty data");
                }
                String cageNum = data.has("cageNumber") && !data.get("cageNumber").isJsonNull() ? data.get("cageNumber").getAsString() : "";
                String rabbitCount = data.has("rabbitCount") && !data.get("rabbitCount").isJsonNull() ? data.get("rabbitCount").getAsString() : "0";
                boolean isFed = data.has("isFed") && !data.get("isFed").isJsonNull() && data.get("isFed").getAsBoolean();
                String lastFeedTime = data.has("lastFeedTime") && !data.get("lastFeedTime").isJsonNull() ? TimeUtil.fmtAny(data.get("lastFeedTime").getAsString()) : "";
                String lastFeedType = data.has("lastFeedType") && !data.get("lastFeedType").isJsonNull() ? data.get("lastFeedType").getAsString() : "";
                String lastFeedAmount = data.has("lastFeedAmount") && !data.get("lastFeedAmount").isJsonNull() ? data.get("lastFeedAmount").getAsString() : "";
                String lastFeedUnit = data.has("lastFeedUnit") && !data.get("lastFeedUnit").isJsonNull() ? data.get("lastFeedUnit").getAsString() : "";
                String abCount = data.has("abnormalUndealCount") && !data.get("abnormalUndealCount").isJsonNull() ? data.get("abnormalUndealCount").getAsString() : "0";
                String lastAbTime = data.has("lastAbnormalTime") && !data.get("lastAbnormalTime").isJsonNull() ? TimeUtil.fmtAny(data.get("lastAbnormalTime").getAsString()) : "";
                String lastAbStatus = data.has("lastAbnormalStatus") && !data.get("lastAbnormalStatus").isJsonNull() ? data.get("lastAbnormalStatus").getAsString() : "";
                JsonArray rabbits = data.has("rabbits") && data.get("rabbits").isJsonArray() ? data.getAsJsonArray("rabbits") : new JsonArray();

                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    if (cageNum != null && !cageNum.trim().isEmpty()) {
                        cageNumber = cageNum;
                        tvMain.setText("笼位：" + cageNum + "  (id=" + cageId + ")");
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("兔只：").append(rabbitCount).append("\n");
                    sb.append("今日已喂：").append(isFed ? "是" : "否").append("\n");
                    if (lastFeedTime == null || lastFeedTime.isEmpty()) {
                        sb.append("最近投喂：无\n");
                    } else {
                        String t = lastFeedType == null ? "" : lastFeedType;
                        String a = lastFeedAmount == null ? "" : lastFeedAmount;
                        String u = lastFeedUnit == null ? "" : lastFeedUnit;
                        sb.append("最近投喂：").append(lastFeedTime).append("  ").append(t);
                        if (!a.isEmpty()) {
                            sb.append("  ").append(a).append(u);
                        }
                        sb.append("\n");
                    }
                    sb.append("异常未处理：").append(abCount);
                    if (lastAbTime != null && !lastAbTime.isEmpty()) {
                        sb.append("\n最近异常：").append(lastAbTime);
                        if (lastAbStatus != null && !lastAbStatus.isEmpty()) {
                            sb.append("  ").append(lastAbStatus);
                        }
                    }
                    if (rabbits != null && rabbits.size() > 0) {
                        sb.append("\n\n笼内兔子(前").append(Math.min(5, rabbits.size())).append(")：");
                        for (int i = 0; i < rabbits.size(); i++) {
                            JsonObject o = rabbits.get(i).isJsonObject() ? rabbits.get(i).getAsJsonObject() : null;
                            if (o == null) {
                                continue;
                            }
                            String rid = o.has("rabbitId") && !o.get("rabbitId").isJsonNull() ? o.get("rabbitId").getAsString() : "";
                            String st = o.has("status") && !o.get("status").isJsonNull() ? o.get("status").getAsString() : "";
                            String wt = o.has("weight") && !o.get("weight").isJsonNull() ? o.get("weight").getAsString() : "";
                            sb.append("\n- 兔#").append(rid);
                            if (!st.isEmpty()) {
                                sb.append("  ").append(st);
                            }
                            if (!wt.isEmpty()) {
                                sb.append("  ").append(wt).append("kg");
                            }
                        }
                    }
                    tvInfo.setText(sb.toString());
                    state.hide();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", this::load);
                });
            }
        }).start();
    }
}
