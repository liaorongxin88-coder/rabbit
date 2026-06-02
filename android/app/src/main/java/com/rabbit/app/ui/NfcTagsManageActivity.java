package com.rabbit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.NfcTagCacheStore;
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class NfcTagsManageActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;

    private ApiClient api;
    private SessionStore session;
    private NfcTagCacheStore nfcCache;
    private TwoLineCardAdapter adapter;

    private final List<TagRow> rows = new ArrayList<TagRow>();

    private static class TagRow {
        String tagUid;
        String targetType;
        long targetId;
        long rabbitId;
        long recordId;
        String targetName;
        String remark;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        api = new ApiClient();
        session = new SessionStore(this);
        nfcCache = new NfcTagCacheStore(this);

        lv = findViewById(R.id.lvList);
        pb = findViewById(R.id.pbListLoading);
        state = new StatePanel(this);
        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("NFC 绑定管理");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.VISIBLE);
        btnTopRight.setText("刷新");
        btnTopRight.setOnClickListener(v -> load());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= rows.size()) {
                return;
            }
            showRowActions(rows.get(position));
        });
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
            pb.setVisibility(View.GONE);
            adapter.clear();
            adapter.notifyDataSetChanged();
            state.showEmpty("🔒", "未登录", "请先登录后再管理 NFC 绑定", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            pb.setVisibility(View.GONE);
            adapter.clear();
            adapter.notifyDataSetChanged();
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再管理 NFC 绑定", "关闭", this::finish);
            return;
        }
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/nfc/tags?page=1&pageSize=200", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                parse(arr);
                List<String> items = renderRows();
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    if (items.isEmpty()) {
                        state.showEmpty("暂无绑定", "可先通过“笼位长按绑定”或“快捷绑定”写入标签", "刷新", v -> load());
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    state.showError("加载失败", e.getMessage(), "重试", v -> load());
                });
            }
        }).start();
    }

    private void parse(JsonArray arr) {
        rows.clear();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            TagRow r = new TagRow();
            r.tagUid = safeStr(o, "tagUid");
            r.targetType = safeStr(o, "targetType");
            r.targetId = safeLong(o, "targetId");
            r.rabbitId = safeLong(o, "rabbitId");
            r.recordId = safeLong(o, "recordId");
            r.targetName = safeStr(o, "targetName");
            r.remark = safeStr(o, "remark");
            if (r.tagUid == null || r.tagUid.trim().isEmpty()) {
                continue;
            }
            rows.add(r);
        }
    }

    private List<String> renderRows() {
        List<String> items = new ArrayList<String>();
        for (TagRow r : rows) {
            String type = r.targetType == null ? "" : r.targetType.trim().toUpperCase();
            String tag = "status:" + (type.isEmpty() ? "NFC" : type);
            String title = "UID " + r.tagUid + "||inventory:标签 " + tag;
            String name = r.targetName == null || r.targetName.isEmpty() ? "-" : r.targetName;
            String sub = "目标：" + name + " (" + type + ")"
                    + (r.targetId > 0 ? ("  targetId=" + r.targetId) : "")
                    + (r.rabbitId > 0 ? ("  rabbitId=" + r.rabbitId) : "")
                    + (r.remark == null || r.remark.isEmpty() ? "" : ("\n" + r.remark));
            items.add(title + "\n" + sub);
        }
        return items;
    }

    private void showRowActions(TagRow r) {
        if (r == null) {
            return;
        }
        String[] items = new String[]{"重绑此目标", "解绑"};
        new AlertDialog.Builder(this)
                .setTitle("标签 " + r.tagUid)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        rebind(r);
                    } else if (which == 1) {
                        confirmUnbind(r);
                    }
                })
                .show();
    }

    private void rebind(TagRow r) {
        String type = r.targetType == null ? "" : r.targetType.trim().toUpperCase();
        if ("CAGE".equals(type) && r.targetId > 0) {
            Intent it = new Intent(this, NfcBindActivity.class);
            it.putExtra("cageId", r.targetId);
            if (r.targetName != null && !r.targetName.isEmpty()) {
                it.putExtra("cageNumber", r.targetName);
            }
            startActivity(it);
            return;
        }
        Intent it = new Intent(this, NfcQuickBindActivity.class);
        it.putExtra("presetTargetType", type);
        if (r.rabbitId > 0) {
            it.putExtra("presetRabbitId", r.rabbitId);
        }
        startActivity(it);
    }

    private void confirmUnbind(TagRow r) {
        new AlertDialog.Builder(this)
                .setTitle("解绑标签")
                .setMessage("确认解绑 " + r.tagUid + "？解绑后该标签将不再跳转。")
                .setNegativeButton("取消", null)
                .setPositiveButton("解绑", (d, w) -> doUnbind(r.tagUid))
                .show();
    }

    private void doUnbind(String uid) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty() || houseId <= 0) {
            state.showError("未登录或未选择兔舍", "关闭", this::finish);
            return;
        }
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                api.deleteJson("/api/nfc/tags?tagUid=" + uid, token, houseId);
                nfcCache.remove(uid);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    Toast.makeText(this, "已解绑：" + uid, Toast.LENGTH_SHORT).show();
                    load();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    state.showError(e.getMessage(), "重试", () -> doUnbind(uid));
                });
            }
        }).start();
    }

    private String safeStr(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private long safeLong(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return 0L;
        }
        try {
            return o.get(key).getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
