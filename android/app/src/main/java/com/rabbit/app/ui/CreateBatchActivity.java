package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.RecentStore;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.InputUtil;

import java.util.ArrayList;
import java.util.List;

public class CreateBatchActivity extends AppCompatActivity {
    private EditText etBatchCode;
    private EditText etFemaleIds;
    private Button btnPickFemales;
    private Button btnCreate;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvResult;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;

    private ApiClient api;
    private SessionStore session;
    private RecentStore recentStore;
    private final List<Long> femaleRabbitIds = new ArrayList<Long>();
    private final List<String> femaleRabbitLabels = new ArrayList<String>();
    private boolean posting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_batch);

        api = new ApiClient();
        session = new SessionStore(this);
        recentStore = new RecentStore(this);

        etBatchCode = findViewById(R.id.etBatchCode);
        etFemaleIds = findViewById(R.id.etFemaleRabbitIds);
        btnPickFemales = findViewById(R.id.btnPickFemaleRabbits);
        btnCreate = findViewById(R.id.btnDoCreateBatch);
        pb = findViewById(R.id.pbCreateBatchLoading);
        tvResult = findViewById(R.id.tvCreateBatchResult);
        state = new StatePanel(this);
        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);

        tvTopTitle.setText("创建批次");
        btnTopBack.setOnClickListener(v -> finish());
        refreshHeader();
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());

        etFemaleIds.setFocusable(false);
        etFemaleIds.setClickable(true);
        etFemaleIds.setOnClickListener(v -> pickFemales());
        btnPickFemales.setOnClickListener(v -> pickFemales());
        btnCreate.setOnClickListener(v -> create());
    }

    private void refreshHeader() {
        long houseId = session.getHouseId();
        tvTopHouse.setText("用户：" + safe(session.getUserName()) + "  兔舍ID：" + (houseId <= 0 ? "未选择" : String.valueOf(houseId)));
    }

    private void pickFemales() {
        if (!femaleRabbitIds.isEmpty()) {
            showPickFemaleDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再加载母兔列表", "刷新", this::pickFemales);
            return;
        }
        tvResult.setText("加载母兔列表中...");
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/rabbits?type=0&active=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<Long> ids = new ArrayList<Long>();
                List<String> labels = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    String gender = o.has("gender") && !o.get("gender").isJsonNull() ? o.get("gender").getAsString() : "";
                    if ("1".equals(gender)) {
                        continue;
                    }
                    long id = o.get("id").getAsLong();
                    String cageId = o.has("cageId") && !o.get("cageId").isJsonNull() ? o.get("cageId").getAsString() : "";
                    ids.add(id);
                    labels.add("兔#" + id + "  笼位#" + cageId);
                }
                runOnUiThread(() -> {
                    femaleRabbitIds.clear();
                    femaleRabbitLabels.clear();
                    femaleRabbitIds.addAll(ids);
                    femaleRabbitLabels.addAll(labels);
                    refreshHeader();
                    tvResult.setText("");
                    pb.setVisibility(android.view.View.GONE);
                    if (femaleRabbitIds.isEmpty()) {
                        state.showEmpty("🐇", "没有可用母兔", "需要在场的母兔(type=0, gender!=1, active=true)", "刷新", this::pickFemales);
                        return;
                    }
                    showPickFemaleDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::pickFemales);
                });
            }
        }).start();
    }

    private void showPickFemaleDialog() {
        List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
        for (int i = 0; i < femaleRabbitIds.size(); i++) {
            long id = femaleRabbitIds.get(i);
            String label = i < femaleRabbitLabels.size() ? femaleRabbitLabels.get(i) : String.valueOf(id);
            items.add(new PickerDialogUtil.PickItem(id, label, label));
        }
        List<Long> current = InputUtil.parseIds(etFemaleIds.getText().toString().trim());
        List<Long> recent = recentStore.getIds("female_rabbit_" + session.getHouseId());
        PickerDialogUtil.showMulti(this, "选择母兔（可多选）", items, recent, current, picked -> {
            if (picked == null || picked.isEmpty()) {
                Toast.makeText(this, "未选择", Toast.LENGTH_SHORT).show();
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < picked.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(picked.get(i).id);
            }
            etFemaleIds.setText(sb.toString());
            for (int i = picked.size() - 1; i >= 0; i--) {
                recentStore.push("female_rabbit_" + session.getHouseId(), picked.get(i).id, 30);
            }
            Toast.makeText(this, "已选择 " + picked.size() + " 只", Toast.LENGTH_SHORT).show();
        });
    }

    private void create() {
        if (posting) {
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        String code = etBatchCode.getText().toString().trim();
        List<Long> ids = InputUtil.parseIds(etFemaleIds.getText().toString().trim());

        if (houseId <= 0) {
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再创建批次", "刷新", this::create);
            return;
        }
        if (code.isEmpty()) {
            tvResult.setText("请填写批次编号");
            return;
        }
        if (ids == null || ids.isEmpty()) {
            tvResult.setText("请选择母兔");
            return;
        }

        tvResult.setText("");
        posting = true;
        btnCreate.setEnabled(false);
        btnPickFemales.setEnabled(false);
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("batchCode", code);
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                JsonArray arr = new JsonArray();
                for (Long id : ids) {
                    arr.add(id);
                }
                body.add("femaleRabbitIds", arr);
                api.postJson("/api/batches", token, houseId, body);
                runOnUiThread(() -> {
                    tvResult.setText("创建成功");
                    pb.setVisibility(android.view.View.GONE);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvResult.setText(e.getMessage());
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::create);
                    btnCreate.setEnabled(true);
                    btnPickFemales.setEnabled(true);
                    posting = false;
                });
            }
        }).start();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
