package com.rabbit.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AuditLogsActivity extends AppCompatActivity {
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private ArrayAdapter<String> adapter;
    private ApiClient.DownloadHandle downloading;

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

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("审计日志");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.VISIBLE);
        btnTopRight.setText("导出CSV");
        btnTopRight.setOnClickListener(v -> exportCsv());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            state.showEmpty("🔒", "未登录", "请先登录", "关闭", () -> finish());
            return;
        }
        if (houseId <= 0) {
            state.showEmpty("🏠", "未选择兔舍", "请选择兔舍后再查看审计日志", "关闭", () -> finish());
            return;
        }
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/audit-logs?page=1&pageSize=200", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i).isJsonObject()) {
                        continue;
                    }
                    JsonObject o = arr.get(i).getAsJsonObject();
                    String ct = o.has("createTime") && !o.get("createTime").isJsonNull() ? TimeUtil.fmtAny(o.get("createTime").getAsString()) : "";
                    String method = o.has("method") && !o.get("method").isJsonNull() ? o.get("method").getAsString() : "";
                    String path = o.has("path") && !o.get("path").isJsonNull() ? o.get("path").getAsString() : "";
                    long userId = o.has("userId") && !o.get("userId").isJsonNull() ? o.get("userId").getAsLong() : 0L;
                    int status = o.has("status") && !o.get("status").isJsonNull() ? o.get("status").getAsInt() : 0;
                    int apiCode = o.has("apiCode") && !o.get("apiCode").isJsonNull() ? o.get("apiCode").getAsInt() : 0;
                    long costMs = o.has("costMs") && !o.get("costMs").isJsonNull() ? o.get("costMs").getAsLong() : 0L;
                    String traceId = o.has("traceId") && !o.get("traceId").isJsonNull() ? o.get("traceId").getAsString() : "";
                    String apiMsg = o.has("apiMessage") && !o.get("apiMessage").isJsonNull() ? o.get("apiMessage").getAsString() : "";
                    String line1 = safe(method) + " " + safe(path) + " · " + status + " · " + costMs + "ms";
                    String line2 = "时间:" + safe(ct) + "  用户:" + userId + "  code:" + apiCode + (apiMsg.isEmpty() ? "" : (" " + apiMsg)) + "\ntrace:" + safe(traceId);
                    items.add(line1 + "\n" + line2);
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    if (items.isEmpty()) {
                        state.showEmpty("📄", "暂无记录", "没有审计日志", "刷新", this::load);
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::load);
                });
            }
        }).start();
    }

    private void exportCsv() {
        if (downloading != null) {
            Toast.makeText(this, "已有导出进行中", Toast.LENGTH_SHORT).show();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty() || houseId <= 0) {
            return;
        }
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = "audit_logs_house_" + houseId + ".csv";
        File target = new File(dir, fileName);
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        downloading = api.downloadToFileAsync("/api/audit-logs.csv?maxRows=200000", token, houseId, target, new ApiClient.DownloadCallback() {
            @Override
            public void onProgress(long bytesRead, long totalBytes) {
            }

            @Override
            public void onSuccess(File file) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    downloading = null;
                    shareFile(file, "text/csv", "分享审计日志CSV");
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    downloading = null;
                    state.showError(e.getMessage(), "重试", AuditLogsActivity.this::exportCsv);
                });
            }

            @Override
            public void onCanceled() {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    downloading = null;
                    Toast.makeText(AuditLogsActivity.this, "已取消导出", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void shareFile(File f, String mime, String chooserTitle) {
        if (f == null) {
            return;
        }
        try {
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType(mime);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, chooserTitle));
        } catch (Exception e) {
            Toast.makeText(this, (e.getMessage() == null ? "" : e.getMessage()) + "\n已保存：" + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}

