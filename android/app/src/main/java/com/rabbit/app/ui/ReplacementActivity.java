package com.rabbit.app.ui;

import android.os.Bundle;
import android.content.Intent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.RecentStore;
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class ReplacementActivity extends AppCompatActivity {
    private Button btnRefresh;
    private CheckBox cbForceExit;
    private EditText etTargetCageId;
    private Button btnPickTargetCage;
    private Button btnConvert;
    private TextView tvResult;
    private ListView lv;
    private ProgressBar pb;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private RecentStore recentStore;
    private MultiSelectCardAdapter adapter;
    private StatePanel state;
    private final List<Long> rabbitIds = new ArrayList<Long>();
    private Long targetCageId;
    private final List<Long> cageIds = new ArrayList<Long>();
    private final List<String> cageLabels = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replacement);

        btnRefresh = findViewById(R.id.btnRefreshReplacement);
        cbForceExit = findViewById(R.id.cbForceExitBatch);
        etTargetCageId = findViewById(R.id.etTargetCageId);
        btnPickTargetCage = findViewById(R.id.btnPickTargetCage);
        btnConvert = findViewById(R.id.btnConvertReplacement);
        tvResult = findViewById(R.id.tvReplacementResult);
        lv = findViewById(R.id.lvReplacement);
        pb = findViewById(R.id.pbReplacementLoading);

        api = new ApiClient();
        session = new SessionStore(this);
        recentStore = new RecentStore(this);
        adapter = new MultiSelectCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);
        state = new StatePanel(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("留种/转后备兔");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.VISIBLE);
        btnTopRight.setText("记录");
        btnTopRight.setOnClickListener(v -> startActivity(new Intent(this, ReplacementRecordsActivity.class)));
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        btnRefresh.setOnClickListener(v -> load());
        btnConvert.setOnClickListener(v -> convert());
        btnPickTargetCage.setOnClickListener(v -> pickTargetCage());
        etTargetCageId.setFocusable(false);
        etTargetCageId.setClickable(true);
        etTargetCageId.setOnClickListener(v -> pickTargetCage());
        targetCageId = null;
        etTargetCageId.setText("自动（系统选择）");

        lv.setOnItemClickListener((parent, view, position, id) -> {
            adapter.toggle(position);
            tvResult.setText("已选：" + adapter.getCheckedPositions().size() + " 只");
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
        tvResult.setText("");
        state.hide();
        pb.setVisibility(android.view.View.VISIBLE);
        if (houseId <= 0) {
            pb.setVisibility(android.view.View.GONE);
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再刷新列表", "刷新", this::load);
            return;
        }
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/rabbits?type=2&active=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                rabbitIds.clear();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.get("id").getAsLong();
                    long cageId = o.has("cageId") ? o.get("cageId").getAsLong() : 0L;
                    String gender = o.has("gender") ? o.get("gender").getAsString() : "";
                    rabbitIds.add(id);
                    items.add("兔#" + id + "  笼位#" + cageId + "\n性别:" + genderLabel(gender) + "（点选勾选）");
                }
                runOnUiThread(() -> {
                    adapter.setItems(items);
                    adapter.clearChecked();
                    tvResult.setText(items.isEmpty() ? "暂无可转后备的商品兔" : "点选勾选要转后备的商品兔");
                    pb.setVisibility(android.view.View.GONE);
                    if (items.isEmpty()) {
                        state.showEmpty("🐇", "暂无可转后备的商品兔", "没有找到 type=2 的在场商品兔", "刷新", this::load);
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

    private void convert() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            tvResult.setText("请先选择兔舍");
            return;
        }

        JsonArray ids = new JsonArray();
        List<Integer> pos = adapter.getCheckedPositions();
        for (Integer idx : pos) {
            if (idx != null && idx >= 0 && idx < rabbitIds.size()) {
                ids.add(rabbitIds.get(idx));
            }
        }
        if (ids.size() == 0) {
            tvResult.setText("请勾选要转后备的商品兔");
            return;
        }

        boolean forceExit = cbForceExit.isChecked();

        tvResult.setText("");
        state.hide();
        pb.setVisibility(android.view.View.VISIBLE);
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.add("rabbitIds", ids);
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                body.addProperty("forceExitBatch", forceExit);
                if (targetCageId != null && targetCageId > 0) {
                    body.addProperty("targetCageId", targetCageId);
                }
                api.postJson("/api/rabbits/replacement", token, houseId, body);
                runOnUiThread(() -> {
                    tvResult.setText("ok");
                    pb.setVisibility(android.view.View.GONE);
                    load();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::convert);
                });
            }
        }).start();
    }

    private void pickTargetCage() {
        if (!cageIds.isEmpty()) {
            showPickTargetCageDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            Toast.makeText(this, "请先选择兔舍", Toast.LENGTH_SHORT).show();
            return;
        }
        tvResult.setText("加载笼位中...");
        pb.setVisibility(android.view.View.VISIBLE);
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/cages", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<Long> ids = new ArrayList<Long>();
                List<String> labels = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                    String num = o.has("cageNumber") && !o.get("cageNumber").isJsonNull() ? o.get("cageNumber").getAsString() : "";
                    String status = o.has("status") && !o.get("status").isJsonNull() ? o.get("status").getAsString() : "";
                    boolean enabled = !o.has("isEnabled") || o.get("isEnabled").isJsonNull() || o.get("isEnabled").getAsBoolean();
                    if (id <= 0) {
                        continue;
                    }
                    if (!enabled) {
                        continue;
                    }
                    if (!"0".equals(status) && !"2".equals(status)) {
                        continue;
                    }
                    ids.add(id);
                    labels.add("笼位#" + id + "  " + num);
                }
                runOnUiThread(() -> {
                    cageIds.clear();
                    cageLabels.clear();
                    cageIds.addAll(ids);
                    cageLabels.addAll(labels);
                    tvResult.setText("");
                    pb.setVisibility(android.view.View.GONE);
                    showPickTargetCageDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::pickTargetCage);
                });
            }
        }).start();
    }

    private void showPickTargetCageDialog() {
        List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
        items.add(new PickerDialogUtil.PickItem(0, "自动（系统选择）", "自动"));
        for (int i = 0; i < cageIds.size(); i++) {
            long id = cageIds.get(i);
            String label = i < cageLabels.size() ? cageLabels.get(i) : String.valueOf(id);
            items.add(new PickerDialogUtil.PickItem(id, label, label));
        }
        List<Long> recent = new ArrayList<Long>();
        recent.add(0L);
        recent.addAll(recentStore.getIds("replacement_target_cage_" + session.getHouseId()));
        PickerDialogUtil.showSingle(this, "选择目标后备笼位", items, recent, it -> {
            if (it == null) {
                return;
            }
            if (it.id <= 0) {
                targetCageId = null;
                etTargetCageId.setText("自动（系统选择）");
                Toast.makeText(this, "已设置为自动", Toast.LENGTH_SHORT).show();
                return;
            }
            targetCageId = it.id;
            etTargetCageId.setText(it.label);
            recentStore.push("replacement_target_cage_" + session.getHouseId(), it.id, 20);
            Toast.makeText(this, "已选择目标笼位", Toast.LENGTH_SHORT).show();
        });
    }

    private String genderLabel(String g) {
        if ("0".equals(g)) {
            return "母";
        }
        if ("1".equals(g)) {
            return "公";
        }
        return g == null ? "" : g;
    }
}
