package com.rabbit.app.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.NfcCachedTarget;
import com.rabbit.app.storage.NfcTagCacheStore;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.NfcUtil;

import java.util.UUID;

public class NfcBindActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;

    private TextView tvCage;
    private TextView tvResult;
    private ProgressBar pb;
    private StatePanel state;

    private ApiClient api;
    private SessionStore session;
    private NfcTagCacheStore nfcCache;
    private boolean binding;

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] filters;
    private String[][] techLists;

    private long cageId;
    private String cageNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_bind);

        api = new ApiClient();
        session = new SessionStore(this);
        nfcCache = new NfcTagCacheStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        tvCage = findViewById(R.id.tvBindCage);
        tvResult = findViewById(R.id.tvBindResult);
        pb = findViewById(R.id.pbNfcBindLoading);
        state = new StatePanel(this);

        tvTopTitle.setText("绑定NFC");
        btnTopBack.setOnClickListener(v -> finish());
        refreshHeader();
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());

        Intent it = getIntent();
        cageId = it == null ? 0L : it.getLongExtra("cageId", 0L);
        cageNumber = it == null ? null : it.getStringExtra("cageNumber");
        tvCage.setText("笼位：" + (cageNumber == null ? String.valueOf(cageId) : cageNumber) + "  (id=" + cageId + ")");

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            tvResult.setText("");
            state.showEmpty("📶", "设备不支持NFC", "当前手机没有NFC能力，无法进行笼位绑定", "关闭", this::finish);
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

    private void handleTag(Intent intent) {
        if (cageId <= 0) {
            tvResult.setText("");
            state.showError("cageId缺失", "关闭", this::finish);
            return;
        }
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再进行绑定", "关闭", this::finish);
            return;
        }
        String token = session.getToken();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再进行绑定", "去登录", () -> {
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
        bind(uid);
    }

    private void bind(String uid) {
        if (binding) {
            return;
        }
        long houseId = session.getHouseId();
        String token = session.getToken();
        if (token == null || token.trim().isEmpty() || houseId <= 0) {
            state.showError("未登录或未选择兔舍", "关闭", this::finish);
            return;
        }
        binding = true;
        runOnUiThread(() -> {
            tvResult.setText("读取到标签：" + uid + "，正在绑定...");
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        long cid = cageId;
        String cno = cageNumber == null ? String.valueOf(cageId) : cageNumber;
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("cageId", cid);
                body.addProperty("tagUid", uid);
                body.addProperty("requestId", UUID.randomUUID().toString());
                api.postJson("/api/cages/nfc-tags", token, houseId, body);
                NfcCachedTarget ct = new NfcCachedTarget();
                ct.setHouseId(houseId);
                ct.setTargetType("CAGE");
                ct.setTargetId(cid);
                ct.setTargetName(cno);
                nfcCache.put(uid, ct);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    tvResult.setText("绑定成功：" + uid);
                    state.hide();
                    binding = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", () -> bind(uid));
                    binding = false;
                });
            }
        }).start();
    }

    private void refreshHeader() {
        long houseId = session.getHouseId();
        tvTopHouse.setText("用户：" + safe(session.getUserName()) + "  兔舍ID：" + (houseId <= 0 ? "未选择" : String.valueOf(houseId)));
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
