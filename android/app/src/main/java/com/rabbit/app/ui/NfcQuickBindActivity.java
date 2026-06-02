package com.rabbit.app.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.NfcCachedTarget;
import com.rabbit.app.storage.NfcTagCacheStore;
import com.rabbit.app.storage.RecentStore;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.NfcUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
 
public class NfcQuickBindActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
 
    private Spinner spTarget;
    private TextView tvRabbitSelected;
    private Button btnPickRabbit;
    private TextView tvResult;
    private ProgressBar pb;
    private StatePanel state;
 
    private ApiClient api;
    private SessionStore session;
    private RecentStore recentStore;
    private NfcTagCacheStore nfcCache;
 
    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] filters;
    private String[][] techLists;
 
    private long selectedRabbitId;
    private final List<Long> rabbitIds = new ArrayList<Long>();
    private final List<String> rabbitLabels = new ArrayList<String>();
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_quick_bind);

        api = new ApiClient();
        session = new SessionStore(this);
        recentStore = new RecentStore(this);
        nfcCache = new NfcTagCacheStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);

        spTarget = findViewById(R.id.spNfcTarget);
        tvRabbitSelected = findViewById(R.id.tvNfcRabbitSelected);
        btnPickRabbit = findViewById(R.id.btnPickNfcRabbit);
        tvResult = findViewById(R.id.tvNfcBindResult);
        pb = findViewById(R.id.pbNfcQuickBindLoading);
        state = new StatePanel(this);

        tvTopTitle.setText("NFC 快捷绑定");
        btnTopBack.setOnClickListener(v -> finish());
        refreshHeader();
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());

        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{
                "投喂页面(FEED)",
                "治疗/复查(TREATMENT)",
                "销售出栏(SALE)"
        });
        spTarget.setAdapter(ad);
        String presetType = getIntent() == null ? null : getIntent().getStringExtra("presetTargetType");
        long presetRabbitId = getIntent() == null ? 0L : getIntent().getLongExtra("presetRabbitId", 0L);
        if (presetRabbitId > 0) {
            selectedRabbitId = presetRabbitId;
        }
        int presetPos = 0;
        if (presetType != null) {
            String t = presetType.trim().toUpperCase();
            if ("TREATMENT".equals(t)) {
                presetPos = 1;
            } else if ("SALE".equals(t)) {
                presetPos = 2;
            } else {
                presetPos = 0;
            }
        }
        spTarget.setSelection(presetPos);
        spTarget.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateTargetUi();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                updateTargetUi();
            }
        });

        btnPickRabbit.setOnClickListener(v -> pickRabbit());
        updateTargetUi();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            tvResult.setText("");
            state.showEmpty("📶", "设备不支持NFC", "当前手机没有NFC能力，无法使用快捷绑定", "关闭", this::finish);
            return;
        }
        Intent self = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            piFlags |= PendingIntent.FLAG_MUTABLE;
        }
        pendingIntent = PendingIntent.getActivity(this, 0, self, piFlags);
        filters = new IntentFilter[]{
                new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        };
        techLists = new String[][]{
                new String[]{"android.nfc.tech.Ndef"},
                new String[]{"android.nfc.tech.NfcA"},
                new String[]{"android.nfc.tech.NfcB"},
                new String[]{"android.nfc.tech.NfcF"},
                new String[]{"android.nfc.tech.NfcV"},
                new String[]{"android.nfc.tech.IsoDep"},
                new String[]{"android.nfc.tech.MifareUltralight"}
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHeader();
        if (nfcAdapter != null) {
            if (!nfcAdapter.isEnabled()) {
                tvResult.setText("");
                state.showEmpty("📶", "请先打开系统NFC开关", "打开后返回本页，靠近标签即可绑定", "关闭", this::finish);
                return;
            }
            try {
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, techLists);
                state.hide();
            } catch (Exception e) {
                tvResult.setText("");
                state.showError(e.getMessage(), "重试", () -> onResume());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableForegroundDispatch(this);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleTag(intent);
    }

    private void updateTargetUi() {
        String type = getSelectedTargetType();
        boolean showRabbit = "TREATMENT".equals(type);
        tvRabbitSelected.setVisibility(showRabbit ? View.VISIBLE : View.GONE);
        btnPickRabbit.setVisibility(showRabbit ? View.VISIBLE : View.GONE);
        if (showRabbit) {
            tvRabbitSelected.setText(selectedRabbitId > 0 ? ("兔#" + selectedRabbitId) : "兔：未选择");
        }
    }

    private String getSelectedTargetType() {
        int p = spTarget.getSelectedItemPosition();
        if (p == 1) {
            return "TREATMENT";
        }
        if (p == 2) {
            return "SALE";
        }
        return "FEED";
    }

    private void handleTag(Intent intent) {
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再进行NFC绑定", "关闭", this::finish);
            return;
        }
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再进行NFC绑定", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        String uid = NfcUtil.getTagUid(intent);
        if (uid == null || uid.isEmpty()) {
            tvResult.setText("");
            state.showError("读取NFC失败", "重试", () -> handleTag(intent));
            return;
        }
        String type = getSelectedTargetType();
        if ("TREATMENT".equals(type) && selectedRabbitId <= 0) {
            tvResult.setText("");
            state.showEmpty("🐇", "治疗绑定需要先选择兔子", "先选一只兔，再靠近标签进行绑定", "选择兔子", this::pickRabbit);
            return;
        }
        bind(uid, type, selectedRabbitId);
    }

    private void bind(String uid, String type, long rabbitId) {
        if (binding) {
            return;
        }
        long houseId = session.getHouseId();
        String token = session.getToken();
        binding = true;
        runOnUiThread(() -> {
            tvResult.setText("读取到标签：" + uid + "，正在绑定...");
            pb.setVisibility(View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("tagUid", uid);
                body.addProperty("targetType", type);
                if ("TREATMENT".equals(type)) {
                    body.addProperty("rabbitId", rabbitId);
                }
                body.addProperty("requestId", UUID.randomUUID().toString());
                api.postJson("/api/nfc/tags", token, houseId, body);
                NfcCachedTarget ct = new NfcCachedTarget();
                ct.setHouseId(houseId);
                ct.setTargetType(type);
                if ("TREATMENT".equals(type)) {
                    ct.setRabbitId(rabbitId);
                }
                nfcCache.put(uid, ct);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    tvResult.setText("绑定成功：" + uid + " → " + type);
                    state.hide();
                    binding = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", () -> bind(uid, type, rabbitId));
                    binding = false;
                });
            }
        }).start();
    }

    private void pickRabbit() {
        if (!rabbitIds.isEmpty()) {
            showPickRabbitDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再加载兔子列表", "关闭", this::finish);
            return;
        }
        tvResult.setText("加载兔子列表中...");
        pb.setVisibility(View.VISIBLE);
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
                    pb.setVisibility(View.GONE);
                    if (rabbitIds.isEmpty()) {
                        state.showEmpty("🐇", "暂无兔子", "先去“兔子”页面录入，再回来绑定治疗/复查", "关闭", this::finish);
                        return;
                    }
                    showPickRabbitDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
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
        List<Long> recent = recentStore.getIds("nfc_treatment_rabbit_" + session.getHouseId());
        PickerDialogUtil.showSingle(this, "选择兔子", items, recent, it -> {
            if (it == null) {
                return;
            }
            selectedRabbitId = it.id;
            recentStore.push("nfc_treatment_rabbit_" + session.getHouseId(), it.id, 30);
            updateTargetUi();
        });
    }

    private void refreshHeader() {
        long houseId = session.getHouseId();
        tvTopHouse.setText("用户：" + safe(session.getUserName()) + "  兔舍ID：" + (houseId <= 0 ? "未选择" : String.valueOf(houseId)));
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
