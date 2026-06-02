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
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class HardwareControlActivity extends AppCompatActivity {
    private TextView tvStatus;
    private Button btnRefresh;
    private EditText etBatchId;
    private EditText etRabbitIds;
    private Button btnStart;
    private Button btnFinish;
    private ProgressBar pb;
    private StatePanel state;

    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private boolean acting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hardware_control);

        tvStatus = findViewById(R.id.tvHardwareStatus);
        btnRefresh = findViewById(R.id.btnHardwareRefresh);
        etBatchId = findViewById(R.id.etHardwareBatchId);
        etRabbitIds = findViewById(R.id.etHardwareRabbitIds);
        btnStart = findViewById(R.id.btnHardwareStart);
        btnFinish = findViewById(R.id.btnHardwareFinish);
        pb = findViewById(R.id.pbHardwareLoading);
        state = new StatePanel(this);

        api = new ApiClient();
        session = new SessionStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("硬件控制");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        btnRefresh.setOnClickListener(v -> loadStatus());
        btnStart.setOnClickListener(v -> callAphrodisiac(true));
        btnFinish.setOnClickListener(v -> callAphrodisiac(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatus();
    }

    private void loadStatus() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            state.showEmpty("🔒", "未登录", "请先登录", "关闭", () -> finish());
            return;
        }
        if (houseId <= 0) {
            state.showEmpty("🏠", "未选择兔舍", "请选择兔舍后再进行硬件操作", "关闭", () -> finish());
            return;
        }
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/hardware/status", token, houseId);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : new JsonObject();
                boolean enabled = data.has("enabled") && !data.get("enabled").isJsonNull() && data.get("enabled").getAsBoolean();
                String type = data.has("type") && !data.get("type").isJsonNull() ? data.get("type").getAsString() : "";
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    tvStatus.setText("enabled=" + enabled + "\ntype=" + type);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::loadStatus);
                });
            }
        }).start();
    }

    private void callAphrodisiac(boolean start) {
        if (acting) {
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty() || houseId <= 0) {
            return;
        }
        long batchId;
        try {
            batchId = Long.parseLong(etBatchId.getText() == null ? "" : etBatchId.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "batchId不合法", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Long> rabbitIds = parseRabbitIds(etRabbitIds.getText() == null ? "" : etRabbitIds.getText().toString());
        if (rabbitIds.isEmpty()) {
            Toast.makeText(this, "rabbitIds不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        acting = true;
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("batchId", batchId);
                JsonArray arr = new JsonArray();
                for (Long id : rabbitIds) {
                    if (id != null && id > 0) {
                        arr.add(id);
                    }
                }
                body.add("rabbitIds", arr);
                api.postJson(start ? "/api/hardware/aphrodisiac/start" : "/api/hardware/aphrodisiac/finish", token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    Toast.makeText(this, start ? "已触发开始" : "已触发结束", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    state.showError(e.getMessage(), "重试", () -> callAphrodisiac(start));
                });
            }
        }).start();
    }

    private List<Long> parseRabbitIds(String s) {
        List<Long> ids = new ArrayList<Long>();
        if (s == null) {
            return ids;
        }
        String[] parts = s.split("[,，\\s]+");
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                long v = Long.parseLong(t);
                if (v > 0) {
                    ids.add(v);
                }
            } catch (Exception ignored) {
            }
        }
        return ids;
    }
}

