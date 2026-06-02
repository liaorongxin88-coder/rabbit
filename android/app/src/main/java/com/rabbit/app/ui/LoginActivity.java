package com.rabbit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;

public class LoginActivity extends AppCompatActivity {
    private EditText etUserName;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnRegister;
    private TextView tvResult;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;

    private ApiClient api;
    private SessionStore session;
    private boolean posting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        api = new ApiClient();
        session = new SessionStore(this);

        etUserName = findViewById(R.id.etUserName);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        tvResult = findViewById(R.id.tvResult);
        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);

        tvTopTitle.setText("登录");
        tvTopHouse.setVisibility(android.view.View.GONE);
        btnTopBack.setVisibility(android.view.View.GONE);

        btnLogin.setOnClickListener(v -> auth(false));
        btnRegister.setOnClickListener(v -> auth(true));
    }

    private void auth(boolean isRegister) {
        if (posting) {
            return;
        }
        String userName = etUserName.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (userName.isEmpty()) {
            etUserName.setError("请输入用户名");
            etUserName.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("请输入密码");
            etPassword.requestFocus();
            return;
        }
        posting = true;
        runOnUiThread(() -> {
            btnLogin.setEnabled(false);
            btnRegister.setEnabled(false);
            tvResult.setText("处理中...");
        });
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("userName", userName);
                body.addProperty("password", password);
                String path = isRegister ? "/api/auth/register" : "/api/auth/login";
                JsonObject resp = api.postJson(path, null, null, body);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : null;
                if (data == null) {
                    throw new RuntimeException("empty data");
                }
                String token = data.get("token").getAsString();
                long userId = data.get("userId").getAsLong();
                String u = data.get("userName").getAsString();
                session.setToken(token);
                session.setUserId(userId);
                session.setUserName(u);
                runOnUiThread(() -> {
                    tvResult.setText("ok");
                    Intent it = new Intent(this, MainActivity.class);
                    copyNfcExtras(it);
                    startActivity(it);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvResult.setText(e.getMessage());
                    btnLogin.setEnabled(true);
                    btnRegister.setEnabled(true);
                    posting = false;
                });
            }
        }).start();
    }

    private void copyNfcExtras(Intent target) {
        Intent src = getIntent();
        if (src == null || target == null) {
            return;
        }
        long h = src.getLongExtra("nfcHouseId", 0L);
        long c = src.getLongExtra("nfcCageId", 0L);
        String cn = src.getStringExtra("nfcCageNumber");
        String uid = src.getStringExtra("nfcTagUid");
        String type = src.getStringExtra("nfcTargetType");
        long rid = src.getLongExtra("nfcRabbitId", 0L);
        long recId = src.getLongExtra("nfcRecordId", 0L);
        if (h > 0) {
            target.putExtra("nfcHouseId", h);
        }
        if (type != null) {
            target.putExtra("nfcTargetType", type);
        }
        if (c > 0) {
            target.putExtra("nfcCageId", c);
        }
        if (cn != null) {
            target.putExtra("nfcCageNumber", cn);
        }
        if (rid > 0) {
            target.putExtra("nfcRabbitId", rid);
        }
        if (recId > 0) {
            target.putExtra("nfcRecordId", recId);
        }
        if (uid != null) {
            target.putExtra("nfcTagUid", uid);
        }
    }
}
