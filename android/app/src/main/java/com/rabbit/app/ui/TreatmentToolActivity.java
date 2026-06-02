package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.PendingOp;
import com.rabbit.app.storage.PendingOpStore;
import com.rabbit.app.storage.RecentStore;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.DatePickerUtil;
import com.rabbit.app.util.InputUtil;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class TreatmentToolActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private TextView tvSelected;
    private Button btnPickRabbit;
    private EditText etStartDate;
    private EditText etDiagnosis;
    private EditText etDrug;
    private EditText etDose;
    private EditText etDays;
    private EditText etNextReview;
    private EditText etRemark;
    private Button btnSubmit;
    private TextView tvResult;
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;

    private ApiClient api;
    private SessionStore session;
    private RecentStore recentStore;
    private PendingOpStore pendingStore;
    private boolean posting;

    private long selectedRabbitId;
    private long focusTreatmentId;

    private final List<Long> rabbitIds = new ArrayList<Long>();
    private final List<String> rabbitLabels = new ArrayList<String>();
    private ArrayAdapter<String> adapter;
    private final List<Long> recordIds = new ArrayList<Long>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_treatment_tool);

        api = new ApiClient();
        session = new SessionStore(this);
        recentStore = new RecentStore(this);
        pendingStore = new PendingOpStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("治疗/复查");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        tvSelected = findViewById(R.id.tvTreatmentSelected);
        btnPickRabbit = findViewById(R.id.btnPickTreatmentRabbit);
        etStartDate = findViewById(R.id.etTreatmentStartDate);
        etDiagnosis = findViewById(R.id.etTreatmentDiagnosis);
        etDrug = findViewById(R.id.etTreatmentDrug);
        etDose = findViewById(R.id.etTreatmentDose);
        etDays = findViewById(R.id.etTreatmentDays);
        etNextReview = findViewById(R.id.etTreatmentNextReview);
        etRemark = findViewById(R.id.etTreatmentRemark);
        btnSubmit = findViewById(R.id.btnSubmitTreatment);
        tvResult = findViewById(R.id.tvTreatmentResult);
        lv = findViewById(R.id.lvTreatmentHistory);
        pb = findViewById(R.id.pbTreatmentLoading);
        state = new StatePanel(this);

        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        etStartDate.setText(TimeUtil.today());
        DatePickerUtil.attach(this, etStartDate);
        DatePickerUtil.attach(this, etNextReview);

        btnPickRabbit.setOnClickListener(v -> pickRabbit());
        btnSubmit.setOnClickListener(v -> submitTreatment());

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= recordIds.size()) {
                return;
            }
            long rid = recordIds.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("操作")
                    .setItems(new String[]{"复查完成"}, (d, which) -> {
                        if (which == 0) {
                            completeTreatment(rid);
                        }
                    })
                    .show();
        });

        selectedRabbitId = getIntent().getLongExtra("rabbitId", 0L);
        focusTreatmentId = getIntent().getLongExtra("treatmentId", 0L);
        if (selectedRabbitId > 0) {
            tvSelected.setText("兔#" + selectedRabbitId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedRabbitId > 0) {
            loadHistory();
        }
    }

    private void pickRabbit() {
        if (!rabbitIds.isEmpty()) {
            showPickRabbitDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再选择兔子", "去登录", () -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再选择兔子", "关闭", this::finish);
            return;
        }
        tvResult.setText("加载兔子列表中...");
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/rabbits?active=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<Long> ids = new ArrayList<Long>();
                List<String> labels = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                    String cageId = o.has("cageId") && !o.get("cageId").isJsonNull() ? o.get("cageId").getAsString() : "";
                    String type = o.has("type") && !o.get("type").isJsonNull() ? o.get("type").getAsString() : "";
                    if (id <= 0) {
                        continue;
                    }
                    ids.add(id);
                    labels.add("兔#" + id + "  类型:" + type + "\n笼位#" + cageId);
                }
                runOnUiThread(() -> {
                    rabbitIds.clear();
                    rabbitLabels.clear();
                    rabbitIds.addAll(ids);
                    rabbitLabels.addAll(labels);
                    tvResult.setText("");
                    pb.setVisibility(android.view.View.GONE);
                    if (rabbitIds.isEmpty()) {
                        state.showEmpty("🐇", "暂无兔子", "先去“兔子”页面录入，再回来提交治疗记录", "关闭", this::finish);
                        return;
                    }
                    showPickRabbitDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", this::pickRabbit);
                });
            }
        }).start();
    }

    private void showPickRabbitDialog() {
        List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
        for (int i = 0; i < rabbitIds.size(); i++) {
            long id = rabbitIds.get(i);
            String label = i < rabbitLabels.size() ? rabbitLabels.get(i) : String.valueOf(id);
            items.add(new PickerDialogUtil.PickItem(id, label, label));
        }
        List<Long> recent = recentStore.getIds("treatment_rabbit_" + session.getHouseId());
        PickerDialogUtil.showSingle(this, "选择兔子", items, recent, it -> {
            if (it == null) {
                return;
            }
            selectedRabbitId = it.id;
            tvSelected.setText("兔#" + selectedRabbitId);
            recentStore.push("treatment_rabbit_" + session.getHouseId(), it.id, 30);
            loadHistory();
        });
    }

    private void submitTreatment() {
        if (posting) {
            return;
        }
        if (selectedRabbitId <= 0) {
            tvResult.setText("请先选择兔子");
            return;
        }
        java.util.Date start = InputUtil.parseDate(etStartDate.getText().toString().trim());
        if (start == null) {
            tvResult.setText("日期格式错误：yyyy-MM-dd");
            etStartDate.setError("日期格式错误：yyyy-MM-dd");
            return;
        }
        String diagnosis = etDiagnosis.getText().toString().trim();
        String drug = etDrug.getText().toString().trim();
        String dose = etDose.getText().toString().trim();
        String daysStr = etDays.getText().toString().trim();
        Integer days = null;
        if (!daysStr.isEmpty()) {
            try {
                days = Integer.parseInt(daysStr);
            } catch (Exception ignored) {
            }
        }
        java.util.Date review = InputUtil.parseDate(etNextReview.getText().toString().trim());
        if (!etNextReview.getText().toString().trim().isEmpty() && review == null) {
            tvResult.setText("复查日期格式错误：yyyy-MM-dd");
            etNextReview.setError("日期格式错误：yyyy-MM-dd");
            return;
        }
        if (review == null) {
            review = new java.util.Date(start.getTime() + 3L * 24L * 60L * 60L * 1000L);
            etNextReview.setText(new java.text.SimpleDateFormat("yyyy-MM-dd").format(review));
        }
        String remark = etRemark.getText().toString().trim();

        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再提交", "去登录", () -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再提交", "关闭", this::finish);
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("rabbitId", selectedRabbitId);
        body.addProperty("startDate", start.getTime());
        if (!diagnosis.isEmpty()) {
            body.addProperty("diagnosis", diagnosis);
        }
        if (!drug.isEmpty()) {
            body.addProperty("drug", drug);
        }
        if (!dose.isEmpty()) {
            body.addProperty("dose", dose);
        }
        if (days != null) {
            body.addProperty("days", days);
        }
        if (review != null) {
            body.addProperty("nextReviewDate", review.getTime());
        }
        if (!remark.isEmpty()) {
            body.addProperty("remark", remark);
        }
        body.addProperty("requestId", java.util.UUID.randomUUID().toString());

        String bodyJson = body.toString();
        posting = true;
        btnSubmit.setEnabled(false);
        btnPickRabbit.setEnabled(false);
        tvResult.setText("处理中...");
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                api.postJson("/api/treatments", token, houseId, body);
                runOnUiThread(() -> {
                    tvResult.setText("提交成功");
                    Toast.makeText(this, "提交成功", Toast.LENGTH_SHORT).show();
                    pb.setVisibility(android.view.View.GONE);
                    btnSubmit.setEnabled(true);
                    btnPickRabbit.setEnabled(true);
                    posting = false;
                    loadHistory();
                });
            } catch (Exception e) {
                PendingOp op = new PendingOp();
                op.setId(String.valueOf(System.currentTimeMillis()) + "-" + java.util.UUID.randomUUID().toString());
                op.setTitle("治疗/复查");
                op.setPath("/api/treatments");
                op.setHouseId(houseId);
                op.setCreateTime(System.currentTimeMillis());
                op.setBodyJson(bodyJson);
                pendingStore.add(op);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    tvResult.setText(e.getMessage() + "（已存为待提交，可在“待提交”重试）");
                    state.showError(e.getMessage(), "重试", this::submitTreatment);
                    btnSubmit.setEnabled(true);
                    btnPickRabbit.setEnabled(true);
                    posting = false;
                });
            }
        }).start();
    }

    private void loadHistory() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            adapter.clear();
            adapter.notifyDataSetChanged();
            state.showEmpty("🔒", "未登录", "请先登录后再查看治疗记录", "去登录", () -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            adapter.clear();
            adapter.notifyDataSetChanged();
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再查看治疗记录", "关闭", this::finish);
            return;
        }
        tvResult.setText("");
        state.hide();
        pb.setVisibility(android.view.View.VISIBLE);
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/treatments?rabbitId=" + selectedRabbitId + "&limit=50", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                recordIds.clear();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                    String start = o.has("startDate") && !o.get("startDate").isJsonNull() ? TimeUtil.fmtAny(o.get("startDate").getAsString()) : "";
                    String drug = o.has("drug") && !o.get("drug").isJsonNull() ? o.get("drug").getAsString() : "";
                    String status = o.has("status") && !o.get("status").isJsonNull() ? o.get("status").getAsString() : "";
                    String next = o.has("nextReviewDate") && !o.get("nextReviewDate").isJsonNull() ? TimeUtil.fmtAny(o.get("nextReviewDate").getAsString()) : "";
                    recordIds.add(id);
                    String tag = "treatment:" + (status == null || status.trim().isEmpty() ? "记录" : status.trim());
                    items.add("治疗#" + id + "  " + start + "||" + tag + "\n药品:" + safe(drug) + "  复查:" + safe(next));
                }
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    if (items.isEmpty()) {
                        state.showEmpty("🩺", "暂无治疗记录", "先选择一只兔子，再填写治疗信息提交", null, null);
                    } else {
                        state.hide();
                    }
                    if (focusTreatmentId > 0) {
                        for (int i = 0; i < recordIds.size(); i++) {
                            if (recordIds.get(i) == focusTreatmentId) {
                                lv.smoothScrollToPosition(i);
                                break;
                            }
                        }
                        focusTreatmentId = 0L;
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    tvResult.setText("");
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    state.showError(e.getMessage(), "重试", this::loadHistory);
                });
            }
        }).start();
    }

    private void completeTreatment(long id) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty() || houseId <= 0) {
            state.showError("未登录或未选择兔舍", "关闭", this::finish);
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("completeTime", System.currentTimeMillis());
        body.addProperty("requestId", java.util.UUID.randomUUID().toString());
        String bodyJson = body.toString();
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                api.postJson("/api/treatments/" + id + "/complete", token, houseId, body);
                ackReviewEvent(id);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    Toast.makeText(this, "已完成复查", Toast.LENGTH_SHORT).show();
                    loadHistory();
                });
            } catch (Exception e) {
                PendingOp op = new PendingOp();
                op.setId(String.valueOf(System.currentTimeMillis()) + "-" + java.util.UUID.randomUUID().toString());
                op.setTitle("治疗复查完成");
                op.setPath("/api/treatments/" + id + "/complete");
                op.setHouseId(houseId);
                op.setCreateTime(System.currentTimeMillis());
                op.setBodyJson(bodyJson);
                pendingStore.add(op);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage() + "（已存为待提交）", "重试", () -> completeTreatment(id));
                });
            }
        }).start();
    }

    private void ackReviewEvent(long recordId) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty() || houseId <= 0) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("category", "治疗复查");
        body.addProperty("recordId", recordId);
        body.addProperty("action", "ack");
        try {
            api.postJson("/api/events/ack", token, houseId, body);
        } catch (Exception ignored) {
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
