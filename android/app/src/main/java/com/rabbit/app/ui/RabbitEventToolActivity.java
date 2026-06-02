package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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

public class RabbitEventToolActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private TextView tvSelected;
    private Button btnPickRabbit;
    private Spinner spType;
    private EditText etDate;
    private EditText etReason;
    private CheckBox cbForce;
    private Button btnSubmit;
    private TextView tvResult;
    private ProgressBar pb;
    private StatePanel state;

    private ApiClient api;
    private SessionStore session;
    private RecentStore recentStore;
    private PendingOpStore pendingStore;
    private boolean posting;

    private long selectedRabbitId;
    private final List<Long> rabbitIds = new ArrayList<Long>();
    private final List<String> rabbitLabels = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rabbit_event_tool);

        api = new ApiClient();
        session = new SessionStore(this);
        recentStore = new RecentStore(this);
        pendingStore = new PendingOpStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("兔子事件");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        tvSelected = findViewById(R.id.tvRabbitEventSelected);
        btnPickRabbit = findViewById(R.id.btnPickRabbitForEvent);
        spType = findViewById(R.id.spRabbitEventType);
        etDate = findViewById(R.id.etRabbitEventDate);
        etReason = findViewById(R.id.etRabbitEventReason);
        cbForce = findViewById(R.id.cbRabbitEventForceExit);
        btnSubmit = findViewById(R.id.btnSubmitRabbitEvent);
        tvResult = findViewById(R.id.tvRabbitEventResult);
        pb = findViewById(R.id.pbRabbitEventLoading);
        state = new StatePanel(this);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{"死亡", "淘汰", "隔离", "解除隔离"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(typeAdapter);

        etDate.setText(TimeUtil.today());
        DatePickerUtil.attach(this, etDate);

        btnPickRabbit.setOnClickListener(v -> pickRabbit());
        btnSubmit.setOnClickListener(v -> submit());

        long preRabbitId = getIntent().getLongExtra("rabbitId", 0L);
        if (preRabbitId > 0) {
            selectedRabbitId = preRabbitId;
            tvSelected.setText("兔#" + selectedRabbitId);
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
                    String gender = o.has("gender") && !o.get("gender").isJsonNull() ? o.get("gender").getAsString() : "";
                    if (id <= 0) {
                        continue;
                    }
                    ids.add(id);
                    labels.add("兔#" + id + "  类型:" + type + "  性别:" + gender + "\n笼位#" + cageId);
                }
                runOnUiThread(() -> {
                    rabbitIds.clear();
                    rabbitLabels.clear();
                    rabbitIds.addAll(ids);
                    rabbitLabels.addAll(labels);
                    tvResult.setText("");
                    pb.setVisibility(android.view.View.GONE);
                    if (rabbitIds.isEmpty()) {
                        state.showEmpty("🐇", "暂无兔子", "先去“兔子”页面录入，再回来提交事件", "关闭", this::finish);
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
        List<Long> recent = recentStore.getIds("pick_rabbit_" + session.getHouseId());
        PickerDialogUtil.showSingle(this, "选择兔子", items, recent, it -> {
            if (it == null) {
                return;
            }
            selectedRabbitId = it.id;
            tvSelected.setText("兔#" + selectedRabbitId);
            recentStore.push("pick_rabbit_" + session.getHouseId(), it.id, 30);
        });
    }

    private void submit() {
        if (posting) {
            return;
        }
        if (selectedRabbitId <= 0) {
            tvResult.setText("请先选择兔子");
            return;
        }
        java.util.Date d = InputUtil.parseDate(etDate.getText().toString().trim());
        if (d == null) {
            tvResult.setText("日期格式错误：yyyy-MM-dd");
            etDate.setError("日期格式错误：yyyy-MM-dd");
            return;
        }
        String typeLabel = spType.getSelectedItem() == null ? "" : String.valueOf(spType.getSelectedItem());
        String eventType = mapType(typeLabel);
        if (eventType.isEmpty()) {
            tvResult.setText("eventType不合法");
            return;
        }
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
        String reason = etReason.getText().toString().trim();
        boolean force = cbForce.isChecked();
        JsonObject body = new JsonObject();
        body.addProperty("rabbitId", selectedRabbitId);
        body.addProperty("eventType", eventType);
        body.addProperty("actionDate", d.getTime());
        if (!reason.isEmpty()) {
            body.addProperty("reason", reason);
        }
        body.addProperty("forceExitBatch", force);
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
                api.postJson("/api/rabbits/events", token, houseId, body);
                runOnUiThread(() -> {
                    tvResult.setText("提交成功：" + typeLabel + "  兔#" + selectedRabbitId);
                    Toast.makeText(this, "提交成功", Toast.LENGTH_SHORT).show();
                    pb.setVisibility(android.view.View.GONE);
                    btnSubmit.setEnabled(true);
                    btnPickRabbit.setEnabled(true);
                    posting = false;
                });
            } catch (Exception e) {
                PendingOp op = new PendingOp();
                op.setId(String.valueOf(System.currentTimeMillis()) + "-" + java.util.UUID.randomUUID().toString());
                op.setTitle("兔子事件:" + typeLabel);
                op.setPath("/api/rabbits/events");
                op.setHouseId(houseId);
                op.setCreateTime(System.currentTimeMillis());
                op.setBodyJson(bodyJson);
                pendingStore.add(op);
                runOnUiThread(() -> {
                    tvResult.setText(e.getMessage() + "（已存为待提交，可在“待提交”重试）");
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::submit);
                    btnSubmit.setEnabled(true);
                    btnPickRabbit.setEnabled(true);
                    posting = false;
                });
            }
        }).start();
    }

    private String mapType(String label) {
        if ("死亡".equals(label)) {
            return "death";
        }
        if ("淘汰".equals(label)) {
            return "cull";
        }
        if ("隔离".equals(label)) {
            return "quarantine";
        }
        if ("解除隔离".equals(label)) {
            return "recover";
        }
        return "";
    }
}
