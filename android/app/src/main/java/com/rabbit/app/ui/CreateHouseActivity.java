package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.PendingOp;
import com.rabbit.app.storage.PendingOpStore;
import com.rabbit.app.storage.SessionStore;

public class CreateHouseActivity extends AppCompatActivity {
    private EditText etName;
    private EditText etRows;
    private EditText etCols;
    private EditText etLayers;
    private Button btnCreate;
    private TextView tvResult;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;

    private ApiClient api;
    private SessionStore session;
    private PendingOpStore pendingStore;
    private boolean posting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_house);

        api = new ApiClient();
        session = new SessionStore(this);

        etName = findViewById(R.id.etHouseName);
        etRows = findViewById(R.id.etRows);
        etCols = findViewById(R.id.etCols);
        etLayers = findViewById(R.id.etLayers);
        btnCreate = findViewById(R.id.btnCreateHouse);
        tvResult = findViewById(R.id.tvCreateHouseResult);
        pb = findViewById(R.id.pbCreateHouseLoading);
        state = new StatePanel(this);
        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);

        tvTopTitle.setText("创建兔舍");
        btnTopBack.setOnClickListener(v -> finish());
        tvTopHouse.setText("用户：" + safe(session.getUserName()));

        pendingStore = new PendingOpStore(this);
        btnCreate.setOnClickListener(v -> create());
    }

    private void create() {
        if (posting) {
            return;
        }
        String name = etName.getText().toString().trim();
        int rows = parseInt(etRows.getText().toString().trim());
        int cols = parseInt(etCols.getText().toString().trim());
        int layers = parseInt(etLayers.getText().toString().trim());

        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再创建兔舍", "去登录", () -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (name.isEmpty()) {
            tvResult.setText("请填写兔舍名称");
            etName.setError("必填");
            return;
        }
        if (rows <= 0 || cols <= 0 || layers <= 0) {
            tvResult.setText("排/列/层必须为正整数");
            return;
        }
        tvResult.setText("");
        posting = true;
        btnCreate.setEnabled(false);
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("name", name);
                body.addProperty("layoutRows", rows);
                body.addProperty("layoutCols", cols);
                body.addProperty("layoutLayers", layers);
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                String bodyJson = body.toString();
                JsonObject resp = api.postJson("/api/houses", token, null, body);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : null;
                if (data != null && data.has("id")) {
                    long houseId = data.get("id").getAsLong();
                    session.setHouseId(houseId);
                }
                runOnUiThread(() -> {
                    tvResult.setText("创建成功");
                    pb.setVisibility(android.view.View.GONE);
                    posting = false;
                    finish();
                });
            } catch (Exception e) {
                JsonObject body = new JsonObject();
                body.addProperty("name", name);
                body.addProperty("layoutRows", rows);
                body.addProperty("layoutCols", cols);
                body.addProperty("layoutLayers", layers);
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                String bodyJson = body.toString();
                PendingOp op = new PendingOp();
                op.setId(String.valueOf(System.currentTimeMillis()) + "-" + java.util.UUID.randomUUID().toString());
                op.setTitle("创建兔舍");
                op.setPath("/api/houses");
                op.setHouseId(0L);
                op.setCreateTime(System.currentTimeMillis());
                op.setBodyJson(bodyJson);
                pendingStore.add(op);
                runOnUiThread(() -> {
                    tvResult.setText(e.getMessage() + "（已存为待提交，可在“待提交”重试）");
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::create);
                    btnCreate.setEnabled(true);
                    posting = false;
                });
            }
        }).start();
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
