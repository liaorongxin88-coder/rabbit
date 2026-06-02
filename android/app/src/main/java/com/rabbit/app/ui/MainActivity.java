package com.rabbit.app.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.NfcCachedTarget;
import com.rabbit.app.storage.NfcTagCacheStore;
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_POST_NOTIF = 9001;

    private Button btnLoadHouses;
    private Button btnCreateHouse;
    private Button btnCages;
    private Button btnRabbits;
    private Button btnEvents;
    private Button btnBatches;
    private Button btnFeed;
    private Button btnAbnormal;
    private Button btnWeight;
    private Button btnSales;
    private Button btnReplacement;
    private Button btnReports;
    private Button btnSettings;
    private Button btnPendingOps;
    private TextView tvInfo;
    private ListView lvHouses;
    private ProgressBar pb;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;

    private ApiClient api;
    private SessionStore session;
    private NfcTagCacheStore nfcCache;
    private StatePanel state;
    private boolean loadingHouses;

    private final List<Long> houseIds = new ArrayList<Long>();
    private final List<HouseRow> houseRows = new ArrayList<HouseRow>();
    private ArrayAdapter<String> houseAdapter;

    private long pendingNfcHouseId;
    private long pendingNfcCageId;
    private String pendingNfcCageNumber;
    private String pendingNfcTagUid;
    private String pendingNfcTargetType;
    private long pendingNfcRabbitId;
    private long pendingNfcRecordId;
    private boolean resolvingNfcHouse;

    private static class HouseRow {
        long id;
        String name;
        String remark;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        api = new ApiClient();
        session = new SessionStore(this);
        nfcCache = new NfcTagCacheStore(this);
        if (session.getToken() == null || session.getToken().trim().isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvInfo = findViewById(R.id.tvInfo);
        btnLoadHouses = findViewById(R.id.btnLoadHouses);
        btnCreateHouse = findViewById(R.id.btnCreateHouse);
        btnCages = findViewById(R.id.btnCages);
        btnRabbits = findViewById(R.id.btnRabbits);
        btnEvents = findViewById(R.id.btnEvents);
        btnBatches = findViewById(R.id.btnBatches);
        btnFeed = findViewById(R.id.btnFeed);
        btnAbnormal = findViewById(R.id.btnAbnormal);
        btnWeight = findViewById(R.id.btnWeight);
        btnSales = findViewById(R.id.btnSales);
        btnReplacement = findViewById(R.id.btnReplacement);
        btnReports = findViewById(R.id.btnReports);
        btnSettings = findViewById(R.id.btnSettings);
        btnPendingOps = findViewById(R.id.btnPendingOps);
        lvHouses = findViewById(R.id.lvHouses);
        pb = findViewById(R.id.pbMainLoading);
        state = new StatePanel(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopBack.setVisibility(android.view.View.GONE);
        tvTopTitle.setText("养兔管理");

        houseAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        lvHouses.setAdapter(houseAdapter);

        refreshHeader();
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);
        btnLoadHouses.setOnClickListener(v -> loadHouses());
        btnCreateHouse.setOnClickListener(v -> startActivity(new Intent(this, CreateHouseActivity.class)));

        lvHouses.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < houseIds.size()) {
                long houseId = houseIds.get(position);
                session.setHouseId(houseId);
                refreshHeader();
                loadEventBadge();
            }
        });

        lvHouses.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= houseRows.size()) {
                return false;
            }
            HouseRow row = houseRows.get(position);
            if (row == null || row.id <= 0) {
                return false;
            }
            showHouseActions(row);
            return true;
        });

        btnCages.setOnClickListener(v -> openIfHouseSelected(CagesGridActivity.class));
        btnRabbits.setOnClickListener(v -> openIfHouseSelected(RabbitsActivity.class));
        btnEvents.setOnClickListener(v -> openIfHouseSelected(EventsActivity.class));
        btnBatches.setOnClickListener(v -> openIfHouseSelected(BatchesActivity.class));
        btnFeed.setOnClickListener(v -> openIfHouseSelected(FeedLogsActivity.class));
        btnAbnormal.setOnClickListener(v -> openIfHouseSelected(AbnormalActivity.class));
        btnWeight.setOnClickListener(v -> openIfHouseSelected(WeightLogsActivity.class));
        btnSales.setOnClickListener(v -> openIfHouseSelected(SalesActivity.class));
        btnReplacement.setOnClickListener(v -> openIfHouseSelected(ReplacementActivity.class));
        btnReports.setOnClickListener(v -> openIfHouseSelected(ReportsActivity.class));
        btnSettings.setOnClickListener(v -> openIfHouseSelected(SettingsActivity.class));
        btnPendingOps.setOnClickListener(v -> startActivity(new Intent(this, PendingOpsActivity.class)));

        readNfcExtras(getIntent());
        tryHandlePendingNfc();
        ensureNotificationPermission();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readNfcExtras(intent);
        tryHandlePendingNfc();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHeader();
        loadHouses();
        loadEventBadge();
        tryHandlePendingNfc();
    }

    private void refreshHeader() {
        long houseId = session.getHouseId();
        tvTopHouse.setText("用户：" + safe(session.getUserName()) + "  兔舍ID：" + (houseId <= 0 ? "未选择" : String.valueOf(houseId)));
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIF);
    }

    private void loadHouses() {
        if (loadingHouses) {
            return;
        }
        String token = session.getToken();
        loadingHouses = true;
        runOnUiThread(() -> {
            if (pb != null) {
                pb.setVisibility(android.view.View.VISIBLE);
            }
            if (state != null) {
                state.hide();
            }
        });
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/houses", token, null);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> names = new ArrayList<String>();
                houseIds.clear();
                houseRows.clear();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.get("id").getAsLong();
                    String name = o.get("name").getAsString();
                    String remark = o.has("remark") && !o.get("remark").isJsonNull() ? o.get("remark").getAsString() : "";
                    houseIds.add(id);
                    names.add(id + " " + name);
                    HouseRow row = new HouseRow();
                    row.id = id;
                    row.name = name;
                    row.remark = remark;
                    houseRows.add(row);
                }
                runOnUiThread(() -> {
                    houseAdapter.clear();
                    houseAdapter.addAll(names);
                    houseAdapter.notifyDataSetChanged();
                    String t = names.isEmpty() ? "暂无兔舍，先点“创建兔舍”" : "点击某条兔舍即可选中为当前兔舍";
                    tvInfo.setText(t);
                    if (pb != null) {
                        pb.setVisibility(android.view.View.GONE);
                    }
                    if (state != null) {
                        if (names.isEmpty()) {
                            state.showEmpty("🏠", "暂无兔舍", "先创建兔舍，然后开始记录", "创建兔舍", () -> startActivity(new Intent(this, CreateHouseActivity.class)));
                        } else {
                            state.hide();
                        }
                    }
                    if (session.getHouseId() <= 0 && houseIds.size() == 1 && pendingNfcTagUid != null && !pendingNfcTagUid.isEmpty() && pendingNfcHouseId <= 0) {
                        session.setHouseId(houseIds.get(0));
                        refreshHeader();
                        tryHandlePendingNfc();
                    }
                    loadingHouses = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvInfo.setText(e.getMessage());
                    if (pb != null) {
                        pb.setVisibility(android.view.View.GONE);
                    }
                    if (state != null) {
                        state.showError(e.getMessage(), "重试", this::loadHouses);
                    }
                    loadingHouses = false;
                });
            }
        }).start();
    }

    private void showHouseActions(HouseRow row) {
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        long hid = row.id;
        tvInfo.setText("正在加载兔舍权限...");
        new Thread(() -> {
            try {
                JsonObject perm = api.getJson("/api/houses/permission", token, hid);
                JsonObject p = perm.has("data") && perm.get("data").isJsonObject() ? perm.getAsJsonObject("data") : new JsonObject();
                String perms = p.has("perms") ? p.get("perms").getAsString() : "";
                boolean isAdmin = p.has("isAdmin") && p.get("isAdmin").getAsBoolean();
                boolean canControl = isAdmin || "control".equalsIgnoreCase(perms);
                runOnUiThread(() -> {
                    tvInfo.setText("");
                    if (!canControl) {
                        Toast.makeText(this, "无 control 权限", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] items = isAdmin ? new String[]{"改名/备注", "删除兔舍"} : new String[]{"改名/备注"};
                    new AlertDialog.Builder(this)
                            .setTitle("兔舍 " + row.name + " (ID=" + hid + ")")
                            .setItems(items, (d, which) -> {
                                if (which == 0) {
                                    showEditHouseDialog(row);
                                } else if (which == 1 && isAdmin) {
                                    confirmDeleteHouse(row);
                                }
                            })
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvInfo.setText("");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showEditHouseDialog(HouseRow row) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        box.setPadding(pad, pad, pad, pad);

        EditText etName = new EditText(this);
        etName.setHint("兔舍名称");
        etName.setText(row.name);
        box.addView(etName);

        EditText etRemark = new EditText(this);
        etRemark.setHint("备注（可选）");
        etRemark.setText(row.remark);
        box.addView(etRemark);

        new AlertDialog.Builder(this)
                .setTitle("编辑兔舍")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String name = etName.getText() == null ? "" : etName.getText().toString().trim();
                    String remark = etRemark.getText() == null ? "" : etRemark.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    doUpdateHouse(row.id, name, remark);
                })
                .show();
    }

    private void doUpdateHouse(long houseId, String name, String remark) {
        String token = session.getToken();
        tvInfo.setText("保存中...");
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("name", name);
                if (remark != null && !remark.isEmpty()) {
                    body.addProperty("remark", remark);
                }
                api.putJson("/api/houses/" + houseId, token, houseId, body);
                runOnUiThread(() -> {
                    tvInfo.setText("已更新兔舍");
                    loadHouses();
                    refreshHeader();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvInfo.setText(e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void confirmDeleteHouse(HouseRow row) {
        new AlertDialog.Builder(this)
                .setTitle("删除兔舍")
                .setMessage("确认删除兔舍 " + row.name + "？删除后将不再显示（数据仍保留以防误删）。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> doDeleteHouse(row.id))
                .show();
    }

    private void doDeleteHouse(long houseId) {
        String token = session.getToken();
        tvInfo.setText("删除中...");
        new Thread(() -> {
            try {
                api.deleteJson("/api/houses/" + houseId, token, houseId);
                runOnUiThread(() -> {
                    if (session.getHouseId() == houseId) {
                        session.setHouseId(0L);
                    }
                    refreshHeader();
                    tvInfo.setText("已删除兔舍");
                    loadHouses();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvInfo.setText(e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void readNfcExtras(Intent intent) {
        if (intent == null) {
            return;
        }
        long h = intent.getLongExtra("nfcHouseId", 0L);
        long c = intent.getLongExtra("nfcCageId", 0L);
        String cn = intent.getStringExtra("nfcCageNumber");
        String uid = intent.getStringExtra("nfcTagUid");
        String type = intent.getStringExtra("nfcTargetType");
        long rid = intent.getLongExtra("nfcRabbitId", 0L);
        long recId = intent.getLongExtra("nfcRecordId", 0L);
        if (h > 0) {
            pendingNfcHouseId = h;
            session.setHouseId(h);
            refreshHeader();
        }
        if (c > 0) {
            pendingNfcCageId = c;
        }
        if (cn != null && !cn.trim().isEmpty()) {
            pendingNfcCageNumber = cn.trim();
        }
        if (uid != null && !uid.trim().isEmpty()) {
            pendingNfcTagUid = uid.trim();
        }
        if (type != null && !type.trim().isEmpty()) {
            pendingNfcTargetType = type.trim().toUpperCase();
        }
        if (rid > 0) {
            pendingNfcRabbitId = rid;
        }
        if (recId > 0) {
            pendingNfcRecordId = recId;
        }
        intent.removeExtra("nfcHouseId");
        intent.removeExtra("nfcCageId");
        intent.removeExtra("nfcCageNumber");
        intent.removeExtra("nfcTagUid");
        intent.removeExtra("nfcTargetType");
        intent.removeExtra("nfcRabbitId");
        intent.removeExtra("nfcRecordId");
    }

    private void tryHandlePendingNfc() {
        if (pendingNfcTargetType != null && !pendingNfcTargetType.isEmpty()) {
            String t = pendingNfcTargetType;
            pendingNfcTargetType = null;
            if ("CAGE".equalsIgnoreCase(t) && pendingNfcCageId > 0) {
                openCageSummary(pendingNfcCageId, pendingNfcCageNumber);
                pendingNfcCageId = 0L;
                pendingNfcCageNumber = null;
                pendingNfcTagUid = null;
                pendingNfcHouseId = 0L;
                pendingNfcRabbitId = 0L;
                pendingNfcRecordId = 0L;
                return;
            }
            if ("FEED".equalsIgnoreCase(t)) {
                startActivity(new Intent(this, FeedLogsActivity.class));
                pendingNfcTagUid = null;
                pendingNfcHouseId = 0L;
                pendingNfcRabbitId = 0L;
                pendingNfcRecordId = 0L;
                return;
            }
            if ("SALE".equalsIgnoreCase(t)) {
                startActivity(new Intent(this, SaleToolActivity.class));
                pendingNfcTagUid = null;
                pendingNfcHouseId = 0L;
                pendingNfcRabbitId = 0L;
                pendingNfcRecordId = 0L;
                return;
            }
            if ("TREATMENT".equalsIgnoreCase(t)) {
                Intent it = new Intent(this, TreatmentToolActivity.class);
                if (pendingNfcRabbitId > 0) {
                    it.putExtra("rabbitId", pendingNfcRabbitId);
                }
                if (pendingNfcRecordId > 0) {
                    it.putExtra("treatmentId", pendingNfcRecordId);
                }
                startActivity(it);
                pendingNfcTagUid = null;
                pendingNfcHouseId = 0L;
                pendingNfcRabbitId = 0L;
                pendingNfcRecordId = 0L;
                return;
            }
        }
        if (pendingNfcCageId > 0) {
            openCageSummary(pendingNfcCageId, pendingNfcCageNumber);
            pendingNfcCageId = 0L;
            pendingNfcCageNumber = null;
            pendingNfcTagUid = null;
            pendingNfcHouseId = 0L;
            pendingNfcRabbitId = 0L;
            pendingNfcRecordId = 0L;
            return;
        }
        if (pendingNfcTagUid == null || pendingNfcTagUid.isEmpty()) {
            return;
        }
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            NfcCachedTarget cached = nfcCache.get(pendingNfcTagUid);
            if (cached != null && cached.getHouseId() > 0 && cached.getTargetType() != null && !cached.getTargetType().trim().isEmpty()) {
                session.setHouseId(cached.getHouseId());
                refreshHeader();
                String uid = pendingNfcTagUid;
                pendingNfcTagUid = null;
                tvInfo.setText("离线使用缓存识别：" + uid);
                openNfcTarget(cached.getTargetType(), cached.getTargetId(), cached.getTargetName(), cached.getRabbitId(), cached.getRecordId());
                return;
            }
            if (!resolvingNfcHouse && !houseIds.isEmpty()) {
                resolvingNfcHouse = true;
                String uid = pendingNfcTagUid;
                tvInfo.setText("正在识别所属兔舍...");
                new Thread(() -> {
                    try {
                        String token = session.getToken();
                        if (token == null || token.trim().isEmpty()) {
                            return;
                        }
                        for (Long hid : houseIds) {
                            if (hid == null || hid <= 0) {
                                continue;
                            }
                            try {
                                api.getJson("/api/nfc/resolve?tagUid=" + uid, token, hid);
                                runOnUiThread(() -> {
                                    session.setHouseId(hid);
                                    refreshHeader();
                                    resolvingNfcHouse = false;
                                    tryHandlePendingNfc();
                                });
                                return;
                            } catch (Exception ignored) {
                            }
                        }
                        runOnUiThread(() -> {
                            resolvingNfcHouse = false;
                            tvInfo.setText("已识别NFC标签，但未找到所属兔舍绑定");
                        });
                    } finally {
                        if (!isFinishing()) {
                            runOnUiThread(() -> resolvingNfcHouse = false);
                        }
                    }
                }).start();
            } else {
                tvInfo.setText("已识别NFC标签，但未选择兔舍");
            }
            return;
        }
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        String uid = pendingNfcTagUid;
        pendingNfcTagUid = null;
        tvInfo.setText("正在识别笼位...");
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/nfc/resolve?tagUid=" + uid, token, houseId);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : null;
                if (data == null) {
                    throw new RuntimeException("empty data");
                }
                String targetType = data.has("targetType") && !data.get("targetType").isJsonNull() ? data.get("targetType").getAsString() : "";
                long targetId = data.has("targetId") && !data.get("targetId").isJsonNull() ? data.get("targetId").getAsLong() : 0L;
                String targetName = data.has("targetName") && !data.get("targetName").isJsonNull() ? data.get("targetName").getAsString() : null;
                long rabbitId = data.has("rabbitId") && !data.get("rabbitId").isJsonNull() ? data.get("rabbitId").getAsLong() : 0L;
                long recordId = data.has("recordId") && !data.get("recordId").isJsonNull() ? data.get("recordId").getAsLong() : 0L;
                NfcCachedTarget ct = new NfcCachedTarget();
                ct.setHouseId(houseId);
                ct.setTargetType(targetType);
                ct.setTargetId(targetId);
                ct.setTargetName(targetName);
                ct.setRabbitId(rabbitId);
                ct.setRecordId(recordId);
                nfcCache.put(uid, ct);
                runOnUiThread(() -> openNfcTarget(targetType, targetId, targetName, rabbitId, recordId));
            } catch (Exception e) {
                NfcCachedTarget cached = nfcCache.get(uid);
                if (cached != null && cached.getHouseId() > 0 && cached.getTargetType() != null && !cached.getTargetType().trim().isEmpty()) {
                    runOnUiThread(() -> {
                        if (session.getHouseId() != cached.getHouseId()) {
                            session.setHouseId(cached.getHouseId());
                            refreshHeader();
                        }
                        tvInfo.setText("离线使用缓存识别：" + uid);
                        openNfcTarget(cached.getTargetType(), cached.getTargetId(), cached.getTargetName(), cached.getRabbitId(), cached.getRecordId());
                    });
                    return;
                }
                runOnUiThread(() -> {
                    tvInfo.setText("NFC识别失败：" + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void openNfcTarget(String targetType, long targetId, String targetName, long rabbitId, long recordId) {
        String t = targetType == null ? "" : targetType.trim().toUpperCase();
        if ("CAGE".equals(t)) {
            openCageSummary(targetId, targetName);
            return;
        }
        if ("FEED".equals(t)) {
            startActivity(new Intent(this, FeedLogsActivity.class));
            return;
        }
        if ("SALE".equals(t)) {
            startActivity(new Intent(this, SaleToolActivity.class));
            return;
        }
        if ("TREATMENT".equals(t)) {
            Intent it = new Intent(this, TreatmentToolActivity.class);
            if (rabbitId > 0) {
                it.putExtra("rabbitId", rabbitId);
            }
            if (recordId > 0) {
                it.putExtra("treatmentId", recordId);
            }
            startActivity(it);
            return;
        }
        tvInfo.setText("不支持的NFC目标：" + targetType);
    }

    private void openCage(long cageId, String cageNumber) {
        if (cageId <= 0) {
            tvInfo.setText("未找到笼位");
            return;
        }
        Intent it = new Intent(this, RabbitsActivity.class);
        it.putExtra("cageId", cageId);
        if (cageNumber != null) {
            it.putExtra("cageNumber", cageNumber);
        }
        startActivity(it);
    }

    private void openCageSummary(long cageId, String cageNumber) {
        if (cageId <= 0) {
            tvInfo.setText("未找到笼位");
            return;
        }
        Intent it = new Intent(this, CageSummaryActivity.class);
        it.putExtra("cageId", cageId);
        if (cageNumber != null) {
            it.putExtra("cageNumber", cageNumber);
        }
        startActivity(it);
    }

    private void openIfHouseSelected(Class<?> cls) {
        if (session.getHouseId() <= 0) {
            tvInfo.setText("请先在列表中选择兔舍");
            return;
        }
        startActivity(new Intent(this, cls));
    }

    private void loadEventBadge() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (btnEvents == null) {
            return;
        }
        if (token == null || token.trim().isEmpty() || houseId <= 0) {
            runOnUiThread(() -> btnEvents.setText("提醒"));
            return;
        }
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/events?onlyUnnotified=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                int n = arr.size();
                runOnUiThread(() -> btnEvents.setText(n > 0 ? ("提醒(" + n + ")") : "提醒"));
            } catch (Exception e) {
                runOnUiThread(() -> btnEvents.setText("提醒"));
            }
        }).start();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
