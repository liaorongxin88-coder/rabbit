package com.rabbit.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;

import java.util.UUID;

public class CycleSettingsActivity extends AppCompatActivity {
    private TextView tvInfo;
    private EditText etAphrodisiacDays;
    private EditText etPalpationDays;
    private EditText etPrepartumDays;
    private EditText etWeaningDays;
    private EditText etPostpartumDays;
    private EditText etSaleDays;
    private EditText etReplacementDays;
    private EditText etRemark;
    private Button btnSave;
    private Button btnReload;
    private ProgressBar pb;
    private TextView tvResult;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private StatePanel state;
    private boolean canEdit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cycle_settings);

        api = new ApiClient();
        session = new SessionStore(this);

        tvInfo = findViewById(R.id.tvCycleInfo);
        etAphrodisiacDays = findViewById(R.id.etAphrodisiacDays);
        etPalpationDays = findViewById(R.id.etPalpationDays);
        etPrepartumDays = findViewById(R.id.etPrepartumDays);
        etWeaningDays = findViewById(R.id.etWeaningDays);
        etPostpartumDays = findViewById(R.id.etPostpartumDays);
        etSaleDays = findViewById(R.id.etSaleDays);
        etReplacementDays = findViewById(R.id.etReplacementDays);
        etRemark = findViewById(R.id.etSettingRemark);
        btnSave = findViewById(R.id.btnCycleSave);
        btnReload = findViewById(R.id.btnCycleReload);
        pb = findViewById(R.id.pbCycleLoading);
        tvResult = findViewById(R.id.tvCycleResult);
        state = new StatePanel(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("周期设置");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        btnReload.setOnClickListener(v -> load());
        btnSave.setOnClickListener(v -> save());

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
    }

    private void load() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            tvInfo.setText("");
            btnSave.setEnabled(false);
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再设置周期参数", "刷新", this::load);
            return;
        }
        pb.setVisibility(View.VISIBLE);
        state.hide();
        tvResult.setText("");
        new Thread(() -> {
            try {
                JsonObject permResp = api.getJson("/api/houses/permission", token, houseId);
                JsonObject perm = permResp.has("data") && permResp.get("data").isJsonObject() ? permResp.getAsJsonObject("data") : new JsonObject();
                String p = perm.has("perms") && !perm.get("perms").isJsonNull() ? perm.get("perms").getAsString() : "";
                boolean admin = perm.has("isAdmin") && !perm.get("isAdmin").isJsonNull() && perm.get("isAdmin").getAsBoolean();
                canEdit = admin || "edit".equals(p) || "control".equals(p);

                JsonObject resp = api.getJson("/api/settings", token, houseId);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : new JsonObject();
                runOnUiThread(() -> {
                    tvInfo.setText("我的权限：" + (admin ? "管理员" : ("control".equals(p) ? "可管理" : ("edit".equals(p) ? "可编辑" : "只读"))));
                    btnSave.setEnabled(canEdit);
                    fill(data);
                    pb.setVisibility(View.GONE);
                    state.hide();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    btnSave.setEnabled(false);
                    state.showError(e.getMessage(), "重试", this::load);
                });
            }
        }).start();
    }

    private void fill(JsonObject data) {
        etAphrodisiacDays.setText(num(data, "aphrodisiacDays"));
        etPalpationDays.setText(num(data, "palpationDays"));
        etPrepartumDays.setText(num(data, "prepartumDays"));
        etWeaningDays.setText(num(data, "weaningDays"));
        etPostpartumDays.setText(num(data, "postpartumDays"));
        etSaleDays.setText(num(data, "saleDays"));
        etReplacementDays.setText(num(data, "replacementDays"));
        etRemark.setText(str(data, "remark"));
    }

    private void save() {
        if (!canEdit) {
            tvResult.setText("权限不足");
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        JsonObject body = new JsonObject();
        body.addProperty("requestId", UUID.randomUUID().toString());
        Integer aph = parseInt(etAphrodisiacDays.getText());
        Integer pal = parseInt(etPalpationDays.getText());
        Integer pre = parseInt(etPrepartumDays.getText());
        Integer wea = parseInt(etWeaningDays.getText());
        Integer post = parseInt(etPostpartumDays.getText());
        Integer sale = parseInt(etSaleDays.getText());
        Integer rep = parseInt(etReplacementDays.getText());
        if (aph == null || pal == null || pre == null || wea == null || post == null || sale == null || rep == null) {
            tvResult.setText("天数必须为数字");
            return;
        }
        body.addProperty("aphrodisiacDays", aph);
        body.addProperty("palpationDays", pal);
        body.addProperty("prepartumDays", pre);
        body.addProperty("weaningDays", wea);
        body.addProperty("postpartumDays", post);
        body.addProperty("saleDays", sale);
        body.addProperty("replacementDays", rep);
        String remark = etRemark.getText() == null ? "" : etRemark.getText().toString().trim();
        if (!remark.isEmpty()) {
            body.addProperty("remark", remark);
        }
        pb.setVisibility(View.VISIBLE);
        state.hide();
        tvResult.setText("");
        new Thread(() -> {
            try {
                api.putJson("/api/settings", token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    tvResult.setText("已保存");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    state.showError(e.getMessage(), "重试", this::save);
                });
            }
        }).start();
    }

    private Integer parseInt(CharSequence s) {
        try {
            return Integer.parseInt(s == null ? "" : s.toString().trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String num(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) {
            return "";
        }
        return o.get(k).getAsString();
    }

    private String str(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) {
            return "";
        }
        return o.get(k).getAsString();
    }
}
