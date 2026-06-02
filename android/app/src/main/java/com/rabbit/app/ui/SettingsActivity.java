package com.rabbit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.rabbit.app.Config;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.AppConfigStore;
import com.rabbit.app.storage.SessionStore;

public class SettingsActivity extends AppCompatActivity {
    private TextView tvInfo;
    private Button btnBreedingPerformance;
    private Button btnCycleSettings;
    private Button btnRabbitHistory;
    private Button btnRabbitEventTool;
    private Button btnDepartureRecords;
    private Button btnTreatmentTool;
    private Button btnSaleTool;
    private Button btnInventoryTool;
    private Button btnServerUrl;
    private Button btnHouseMembers;
    private Button btnNfcQuickBind;
    private Button btnNfcManage;
    private Button btnFeedLogBackfill;
    private Button btnEventScan;
    private Button btnEventReminderLogs;
    private Button btnAuditLogs;
    private Button btnHardwareControl;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;

    private AppConfigStore configStore;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        api = new ApiClient();
        session = new SessionStore(this);
        configStore = new AppConfigStore(this);

        tvInfo = findViewById(R.id.tvSettingsInfo);
        btnBreedingPerformance = findViewById(R.id.btnBreedingPerformance);
        btnCycleSettings = findViewById(R.id.btnCycleSettings);
        btnRabbitHistory = findViewById(R.id.btnRabbitHistory);
        btnRabbitEventTool = findViewById(R.id.btnRabbitEventTool);
        btnDepartureRecords = findViewById(R.id.btnDepartureRecords);
        btnTreatmentTool = findViewById(R.id.btnTreatmentTool);
        btnSaleTool = findViewById(R.id.btnSaleTool);
        btnInventoryTool = findViewById(R.id.btnInventoryTool);
        btnServerUrl = findViewById(R.id.btnServerUrl);
        btnHouseMembers = findViewById(R.id.btnHouseMembers);
        btnNfcQuickBind = findViewById(R.id.btnNfcQuickBind);
        btnNfcManage = findViewById(R.id.btnNfcManage);
        btnFeedLogBackfill = findViewById(R.id.btnFeedLogBackfill);
        btnEventScan = findViewById(R.id.btnEventScan);
        btnEventReminderLogs = findViewById(R.id.btnEventReminderLogs);
        btnAuditLogs = findViewById(R.id.btnAuditLogs);
        btnHardwareControl = findViewById(R.id.btnHardwareControl);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("设置");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        btnBreedingPerformance.setOnClickListener(v -> openIfHouseSelected(BreedingPerformanceActivity.class));
        btnCycleSettings.setOnClickListener(v -> openIfHouseSelected(CycleSettingsActivity.class));
        btnRabbitHistory.setOnClickListener(v -> openIfHouseSelected(SelectRabbitActivity.class));
        btnRabbitEventTool.setOnClickListener(v -> openIfHouseSelected(RabbitEventToolActivity.class));
        btnDepartureRecords.setOnClickListener(v -> openIfHouseSelected(DepartureRecordsActivity.class));
        btnTreatmentTool.setOnClickListener(v -> openIfHouseSelected(TreatmentToolActivity.class));
        btnSaleTool.setOnClickListener(v -> openIfHouseSelected(SaleToolActivity.class));
        btnInventoryTool.setOnClickListener(v -> openIfHouseSelected(InventoryActivity.class));
        btnServerUrl.setOnClickListener(v -> showServerUrlDialog());
        btnHouseMembers.setOnClickListener(v -> openIfHouseSelected(HouseMembersActivity.class));
        btnNfcQuickBind.setOnClickListener(v -> openIfHouseSelected(NfcQuickBindActivity.class));
        btnNfcManage.setOnClickListener(v -> openIfHouseSelected(NfcTagsManageActivity.class));
        btnFeedLogBackfill.setOnClickListener(v -> runFeedLogBackfill());
        btnEventScan.setOnClickListener(v -> runEventScan());
        btnEventReminderLogs.setOnClickListener(v -> openIfHouseSelected(EventReminderLogsActivity.class));
        btnAuditLogs.setOnClickListener(v -> openIfHouseSelected(AuditLogsActivity.class));
        btnHardwareControl.setOnClickListener(v -> openIfHouseSelected(HardwareControlActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvInfo.setText("当前兔舍ID：" + session.getHouseId());
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        refreshMyPermission();
    }

    private void openIfHouseSelected(Class<?> cls) {
        if (session.getHouseId() <= 0) {
            tvInfo.setText("请先选择兔舍");
            return;
        }
        startActivity(new Intent(this, cls));
    }

    private void refreshMyPermission() {
        if (session.getHouseId() <= 0) {
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/houses/permission", token, houseId);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : new JsonObject();
                String perms = data.has("perms") && !data.get("perms").isJsonNull() ? data.get("perms").getAsString() : "";
                boolean isAdmin = data.has("isAdmin") && !data.get("isAdmin").isJsonNull() && data.get("isAdmin").getAsBoolean();
                String permText = formatPerm(perms, isAdmin);
                boolean canControl = isAdmin || "control".equals(perms);
                boolean canEdit = isAdmin || "edit".equals(perms) || "control".equals(perms);
                runOnUiThread(() -> {
                    tvInfo.setText("当前兔舍ID：" + houseId + "\n我的权限：" + permText);
                    btnHouseMembers.setEnabled(canControl);
                    if (!canControl) {
                        btnHouseMembers.setText("成员与权限（需管理权限）");
                    } else {
                        btnHouseMembers.setText("成员与权限");
                    }
                    btnCycleSettings.setEnabled(canEdit);
                    btnNfcQuickBind.setEnabled(canControl);
                    if (!canControl) {
                        btnNfcQuickBind.setText("NFC 快捷绑定（需管理权限）");
                    } else {
                        btnNfcQuickBind.setText("NFC 快捷绑定");
                    }
                    btnNfcManage.setEnabled(canControl);
                    if (!canControl) {
                        btnNfcManage.setText("NFC 绑定管理（需管理权限）");
                    } else {
                        btnNfcManage.setText("NFC 绑定管理");
                    }
                    btnFeedLogBackfill.setEnabled(canControl);
                    if (!canControl) {
                        btnFeedLogBackfill.setText("投喂历史修复（需管理权限）");
                    } else {
                        btnFeedLogBackfill.setText("投喂历史修复");
                    }

                    btnEventScan.setEnabled(canControl);
                    if (!canControl) {
                        btnEventScan.setText("提醒扫描(手动触发)（需管理权限）");
                    } else {
                        btnEventScan.setText("提醒扫描(手动触发)");
                    }
                    btnEventReminderLogs.setEnabled(canControl);
                    if (!canControl) {
                        btnEventReminderLogs.setText("提醒扫描日志（需管理权限）");
                    } else {
                        btnEventReminderLogs.setText("提醒扫描日志");
                    }
                    btnAuditLogs.setEnabled(canControl);
                    if (!canControl) {
                        btnAuditLogs.setText("审计日志（需管理权限）");
                    } else {
                        btnAuditLogs.setText("审计日志");
                    }
                    btnHardwareControl.setEnabled(canControl);
                    if (!canControl) {
                        btnHardwareControl.setText("硬件控制（需管理权限）");
                    } else {
                        btnHardwareControl.setText("硬件控制");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvInfo.setText("当前兔舍ID：" + houseId + "\n我的权限：获取失败 " + e.getMessage());
                    btnHouseMembers.setEnabled(false);
                    btnHouseMembers.setText("成员与权限（需管理权限）");
                    btnCycleSettings.setEnabled(false);
                    btnNfcQuickBind.setEnabled(false);
                    btnNfcQuickBind.setText("NFC 快捷绑定（需管理权限）");
                    btnNfcManage.setEnabled(false);
                    btnNfcManage.setText("NFC 绑定管理（需管理权限）");
                    btnFeedLogBackfill.setEnabled(false);
                    btnFeedLogBackfill.setText("投喂历史修复（需管理权限）");

                    btnEventScan.setEnabled(false);
                    btnEventScan.setText("提醒扫描(手动触发)（需管理权限）");
                    btnEventReminderLogs.setEnabled(false);
                    btnEventReminderLogs.setText("提醒扫描日志（需管理权限）");
                    btnAuditLogs.setEnabled(false);
                    btnAuditLogs.setText("审计日志（需管理权限）");
                    btnHardwareControl.setEnabled(false);
                    btnHardwareControl.setText("硬件控制（需管理权限）");
                });
            }
        }).start();
    }

    private String formatPerm(String perms, boolean isAdmin) {
        if (isAdmin) {
            return "管理员";
        }
        if ("control".equals(perms)) {
            return "可管理(control)";
        }
        if ("edit".equals(perms)) {
            return "可编辑(edit)";
        }
        return "只读(view)";
    }

    private void showServerUrlDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        box.setPadding(pad, pad, pad, pad);

        EditText et = new EditText(this);
        et.setHint("http://192.168.1.10:8080");
        et.setText(Config.getBaseUrl());
        box.addView(et);

        new AlertDialog.Builder(this)
                .setTitle("服务器地址(BASE_URL)")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String url = et.getText() == null ? "" : et.getText().toString().trim();
                    if (url.isEmpty()) {
                        Toast.makeText(this, "不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    configStore.setBaseUrl(url);
                    Config.setBaseUrl(url);
                    Toast.makeText(this, "已保存：" + url, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void runFeedLogBackfill() {
        if (session.getHouseId() <= 0) {
            tvInfo.setText("请先选择兔舍");
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        tvInfo.setText("投喂历史修复中...");
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                JsonObject resp = api.postJson("/api/maintenance/feed-log-rabbits/backfill?batchSize=500", token, houseId, body);
                int n;
                try {
                    n = resp.has("data") ? resp.get("data").getAsInt() : 0;
                } catch (Exception ignored) {
                    n = 0;
                }
                int finalN = n;
                runOnUiThread(() -> {
                    tvInfo.setText("当前兔舍ID：" + houseId + "\n投喂历史修复：本次回填 " + finalN + " 条");
                    Toast.makeText(this, "回填 " + finalN + " 条", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvInfo.setText("投喂历史修复失败：" + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void runEventScan() {
        if (session.getHouseId() <= 0) {
            tvInfo.setText("请先选择兔舍");
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        tvInfo.setText("提醒扫描中...");
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                JsonObject resp = api.postJson("/api/maintenance/events/scan", token, houseId, body);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : new JsonObject();
                int prodLogged = data.has("prodLogged") ? data.get("prodLogged").getAsInt() : 0;
                int prodMarked = data.has("prodMarked") ? data.get("prodMarked").getAsInt() : 0;
                int repLogged = data.has("repLogged") ? data.get("repLogged").getAsInt() : 0;
                int repMarked = data.has("repMarked") ? data.get("repMarked").getAsInt() : 0;
                runOnUiThread(() -> {
                    tvInfo.setText("当前兔舍ID：" + houseId
                            + "\n提醒扫描完成："
                            + "\n生产 写日志 " + prodLogged + " / 标记 " + prodMarked
                            + "\n后备 写日志 " + repLogged + " / 标记 " + repMarked);
                    Toast.makeText(this, "扫描完成", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvInfo.setText("提醒扫描失败：" + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
