package com.rabbit.app.ui;

import android.os.Bundle;
import android.content.Intent;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import com.rabbit.app.storage.PendingOp;
import com.rabbit.app.storage.PendingOpStore;
import com.rabbit.app.storage.RecentStore;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.DatePickerUtil;
import com.rabbit.app.util.InputUtil;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class FeedLogsActivity extends AppCompatActivity {
    private TextView tvRabbitsSelected;
    private Button btnPickRabbits;
    private EditText etFeedDate;
    private TextView tvItemSelected;
    private Button btnPickItem;
    private EditText etFeedType;
    private EditText etAmount;
    private EditText etRemark;
    private Button btnSubmit;
    private Button btnRefresh;
    private Button btnLoadMore;
    private TextView tvResult;
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
    private PendingOpStore pendingStore;
    private RecentStore recentStore;
    private final List<String> allItems = new ArrayList<String>();
    private int page = 1;
    private final int pageSize = 20;
    private boolean hasMore = true;
    private boolean loading = false;

    private long selectedItemId = 0L;
    private String selectedItemName = "";
    private String selectedItemUnit = "";
    private final List<Long> invItemIds = new ArrayList<Long>();
    private final List<String> invItemNames = new ArrayList<String>();
    private final List<String> invItemUnits = new ArrayList<String>();
    private final List<String> invItemLabels = new ArrayList<String>();

    private final List<Long> rabbitIds = new ArrayList<Long>();
    private final List<String> rabbitLabels = new ArrayList<String>();
    private final List<Long> selectedRabbitIds = new ArrayList<Long>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed_logs);

        tvRabbitsSelected = findViewById(R.id.tvFeedRabbitsSelected);
        btnPickRabbits = findViewById(R.id.btnPickFeedRabbits);
        etFeedDate = findViewById(R.id.etFeedDate);
        tvItemSelected = findViewById(R.id.tvFeedItemSelected);
        btnPickItem = findViewById(R.id.btnPickFeedItem);
        etFeedType = findViewById(R.id.etFeedType);
        etAmount = findViewById(R.id.etFeedAmount);
        etRemark = findViewById(R.id.etFeedRemark);
        btnSubmit = findViewById(R.id.btnSubmitFeed);
        btnRefresh = findViewById(R.id.btnRefreshFeed);
        btnLoadMore = findViewById(R.id.btnLoadMoreFeed);
        tvResult = findViewById(R.id.tvFeedResult);
        lv = findViewById(R.id.lvFeedLogs);
        pb = findViewById(R.id.pbFeedLoading);
        state = new StatePanel(this);

        api = new ApiClient();
        session = new SessionStore(this);
        pendingStore = new PendingOpStore(this);
        recentStore = new RecentStore(this);
        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);

        tvTopTitle.setText("投喂");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.VISIBLE);
        btnTopRight.setText("刷新");
        btnTopRight.setOnClickListener(v -> load(true));
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        DatePickerUtil.attach(this, etFeedDate);
        btnSubmit.setOnClickListener(v -> submit());
        btnRefresh.setOnClickListener(v -> load(true));
        btnLoadMore.setOnClickListener(v -> load(false));
        btnPickItem.setOnClickListener(v -> pickItem());
        btnPickRabbits.setOnClickListener(v -> pickRabbits());
        refreshItemSelected();
        refreshRabbitsSelected();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(true);
    }

    private void submit() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再提交投喂", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再提交投喂", "关闭", this::finish);
            return;
        }
        List<Long> ids = new ArrayList<Long>(selectedRabbitIds);
        java.util.Date d = InputUtil.parseDate(etFeedDate.getText().toString().trim());
        String type = etFeedType.getText().toString().trim();
        String amount = etAmount.getText().toString().trim();
        String remark = etRemark.getText().toString().trim();

        if (ids.isEmpty()) {
            tvResult.setText("请选择兔");
            return;
        }
        if (d == null) {
            tvResult.setText("日期格式错误：yyyy-MM-dd");
            return;
        }
        if (amount.isEmpty()) {
            tvResult.setText("请填写 amount");
            return;
        }
        try {
            double v = Double.parseDouble(amount);
            if (v <= 0) {
                tvResult.setText("amount 必须大于0");
                return;
            }
        } catch (Exception e) {
            tvResult.setText("amount 格式错误");
            return;
        }

        tvResult.setText("");
        btnSubmit.setEnabled(false);
        pb.setVisibility(android.view.View.VISIBLE);
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Long id : ids) {
            arr.add(id);
        }
        body.add("rabbitIds", arr);
        body.addProperty("requestId", java.util.UUID.randomUUID().toString());
        body.addProperty("feedTime", d.getTime());
        if (selectedItemId > 0) {
            body.addProperty("itemId", selectedItemId);
            body.addProperty("unit", selectedItemUnit);
            if (type.isEmpty()) {
                body.addProperty("feedType", selectedItemName);
            }
        }
        if (!type.isEmpty()) {
            body.addProperty("feedType", type);
        }
        body.addProperty("amount", Double.parseDouble(amount));
        if (!remark.isEmpty()) {
            body.addProperty("remark", remark);
        }
        String bodyJson = body.toString();
        new Thread(() -> {
            try {
                api.postJson("/api/feed-logs", token, houseId, body);
                runOnUiThread(() -> {
                    tvResult.setText("提交成功");
                    Toast.makeText(this, "提交成功", Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                    pb.setVisibility(android.view.View.GONE);
                    load();
                });
            } catch (Exception e) {
                PendingOp op = new PendingOp();
                op.setId(String.valueOf(System.currentTimeMillis()) + "-" + java.util.UUID.randomUUID().toString());
                op.setTitle("投喂");
                op.setPath("/api/feed-logs");
                op.setHouseId(houseId);
                op.setCreateTime(System.currentTimeMillis());
                op.setBodyJson(bodyJson);
                pendingStore.add(op);
                runOnUiThread(() -> {
                    tvResult.setText(e.getMessage() + "（已存为待提交，可在“待提交”重试）");
                    Toast.makeText(this, "提交失败，已存为待提交", Toast.LENGTH_LONG).show();
                    btnSubmit.setEnabled(true);
                    pb.setVisibility(android.view.View.GONE);
                });
            }
        }).start();
    }

    private void load() {
        load(true);
    }

    private void pickRabbits() {
        if (!rabbitIds.isEmpty()) {
            showPickRabbitsDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再选择投喂兔", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再选择投喂兔", "关闭", this::finish);
            return;
        }
        tvResult.setText("加载兔列表中...");
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                List<Long> ids = new ArrayList<Long>();
                List<String> labels = new ArrayList<String>();
                int page = 1;
                int pageSize = 200;
                int total = 0;
                while (total < 2000) {
                    JsonObject resp = api.getJson("/api/rabbits?active=true&page=" + page + "&pageSize=" + pageSize, token, houseId);
                    JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                    if (arr.size() == 0) {
                        break;
                    }
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject o = arr.get(i).getAsJsonObject();
                        long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                        long cageId = o.has("cageId") && !o.get("cageId").isJsonNull() ? o.get("cageId").getAsLong() : 0L;
                        String type = o.has("type") && !o.get("type").isJsonNull() ? o.get("type").getAsString() : "";
                        String label = "兔#" + id + "  笼位#" + cageId;
                        if (!type.isEmpty()) {
                            label = label + "  类型:" + type;
                        }
                        if (id > 0) {
                            ids.add(id);
                            labels.add(label);
                            total++;
                            if (total >= 2000) {
                                break;
                            }
                        }
                    }
                    if (arr.size() < pageSize) {
                        break;
                    }
                    page++;
                }
                runOnUiThread(() -> {
                    rabbitIds.clear();
                    rabbitLabels.clear();
                    rabbitIds.addAll(ids);
                    rabbitLabels.addAll(labels);
                    tvResult.setText("");
                    pb.setVisibility(android.view.View.GONE);
                    if (rabbitIds.isEmpty()) {
                        state.showEmpty("🐇", "没有可用在场兔", "先在“录入兔子”添加在场兔，再回来投喂", "去录入兔子", () -> startActivity(new Intent(this, CreateRabbitActivity.class)));
                        return;
                    }
                    showPickRabbitsDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", this::pickRabbits);
                });
            }
        }).start();
    }

    private void showPickRabbitsDialog() {
        List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
        for (int i = 0; i < rabbitIds.size(); i++) {
            long id = rabbitIds.get(i);
            String label = i < rabbitLabels.size() ? rabbitLabels.get(i) : String.valueOf(id);
            items.add(new PickerDialogUtil.PickItem(id, label, label));
        }
        List<Long> recent = recentStore.getIds("feed_rabbit_" + session.getHouseId());
        List<Long> current = new ArrayList<Long>(new LinkedHashSet<Long>(selectedRabbitIds));
        PickerDialogUtil.showMulti(this, "选择投喂兔（可多选）", items, recent, current, picked -> {
            if (picked == null) {
                return;
            }
            selectedRabbitIds.clear();
            for (PickerDialogUtil.PickItem it : picked) {
                selectedRabbitIds.add(it.id);
            }
            for (int i = picked.size() - 1; i >= 0; i--) {
                recentStore.push("feed_rabbit_" + session.getHouseId(), picked.get(i).id, 30);
            }
            refreshRabbitsSelected();
        });
    }

    private void pickItem() {
        if (!invItemIds.isEmpty()) {
            showPickItemDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再选择物料", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再选择物料", "关闭", this::finish);
            return;
        }
        tvResult.setText("加载物料中...");
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/inventory/items", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<Long> ids = new ArrayList<Long>();
                List<String> names = new ArrayList<String>();
                List<String> units = new ArrayList<String>();
                List<String> labels = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                    String name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : "";
                    String unit = o.has("unit") && !o.get("unit").isJsonNull() ? o.get("unit").getAsString() : "";
                    String qty = o.has("currentQty") && !o.get("currentQty").isJsonNull() ? o.get("currentQty").getAsString() : "0";
                    if (id <= 0 || name.isEmpty()) {
                        continue;
                    }
                    ids.add(id);
                    names.add(name);
                    units.add(unit);
                    labels.add(name + "  库存:" + qty + unit + "\n物料#" + id);
                }
                runOnUiThread(() -> {
                    invItemIds.clear();
                    invItemNames.clear();
                    invItemUnits.clear();
                    invItemLabels.clear();
                    invItemIds.addAll(ids);
                    invItemNames.addAll(names);
                    invItemUnits.addAll(units);
                    invItemLabels.addAll(labels);
                    tvResult.setText("");
                    pb.setVisibility(android.view.View.GONE);
                    if (invItemIds.isEmpty()) {
                        state.showEmpty("📦", "暂无物料", "请先在“库存管理”新增物料，再回来投喂", "去库存", () -> startActivity(new Intent(this, InventoryActivity.class)));
                        return;
                    }
                    showPickItemDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", this::pickItem);
                });
            }
        }).start();
    }

    private void showPickItemDialog() {
        List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
        for (int i = 0; i < invItemIds.size(); i++) {
            long id = invItemIds.get(i);
            String name = i < invItemNames.size() ? invItemNames.get(i) : "";
            String unit = i < invItemUnits.size() ? invItemUnits.get(i) : "";
            String label = i < invItemLabels.size() ? invItemLabels.get(i) : name;
            items.add(new PickerDialogUtil.PickItem(id, label, name));
        }
        List<Long> recent = recentStore.getIds("feed_item_" + session.getHouseId());
        PickerDialogUtil.showSingle(this, "选择物料", items, recent, it -> {
            if (it == null) {
                return;
            }
            int idx = invItemIds.indexOf(it.id);
            selectedItemId = it.id;
            selectedItemName = it.searchKey;
            selectedItemUnit = idx >= 0 && idx < invItemUnits.size() ? invItemUnits.get(idx) : "";
            recentStore.push("feed_item_" + session.getHouseId(), it.id, 30);
            refreshItemSelected();
        });
    }

    private void refreshItemSelected() {
        if (selectedItemId <= 0) {
            tvItemSelected.setText("物料：未选择");
            return;
        }
        tvItemSelected.setText("物料：" + selectedItemName + "（" + selectedItemUnit + "）");
    }

    private void refreshRabbitsSelected() {
        if (selectedRabbitIds.isEmpty()) {
            tvRabbitsSelected.setText("兔：未选择");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("兔：已选 ").append(selectedRabbitIds.size()).append(" 只");
        tvRabbitsSelected.setText(sb.toString());
    }

    private void load(boolean reset) {
        if (loading) {
            return;
        }
        if (!reset && !hasMore) {
            Toast.makeText(this, "没有更多了", Toast.LENGTH_SHORT).show();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (reset) {
            page = 1;
            hasMore = true;
            allItems.clear();
            runOnUiThread(() -> {
                adapter.clear();
                adapter.notifyDataSetChanged();
                state.hide();
            });
        }
        int requestPage = page;
        loading = true;
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            btnLoadMore.setEnabled(false);
            if (reset) {
                state.hide();
            }
        });
        new Thread(() -> {
            try {
                String url = "/api/feed-logs?page=" + requestPage + "&pageSize=" + pageSize;
                JsonObject resp = api.getJson(url, token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    String feedTime = o.has("feedTime") && !o.get("feedTime").isJsonNull() ? TimeUtil.fmtAny(o.get("feedTime").getAsString()) : "";
                    String id = o.has("id") ? o.get("id").getAsString() : "";
                    String amount = o.has("amount") && !o.get("amount").isJsonNull() ? o.get("amount").getAsString() : "";
                    String rabbits = o.has("feedingRabbits") && !o.get("feedingRabbits").isJsonNull() ? o.get("feedingRabbits").getAsString() : "";
                    String type = o.has("feedType") && !o.get("feedType").isJsonNull() ? o.get("feedType").getAsString() : "";
                    String tag = "feed:" + (type == null || type.trim().isEmpty() ? "投喂" : type.trim());
                    allItems.add("投喂#" + id + "  " + feedTime + "  " + amount + "||" + tag + "\n兔:" + safe(rabbits));
                }
                hasMore = arr.size() >= pageSize;
                if (!reset) {
                    page = page + 1;
                } else {
                    page = 2;
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(allItems);
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    btnLoadMore.setEnabled(hasMore);
                    btnLoadMore.setText(hasMore ? "加载更多" : "没有更多了");
                    if (reset && allItems.isEmpty()) {
                        state.showEmpty("🥕", "暂无投喂记录", "先新增物料，再提交投喂，系统会自动扣库存", "去库存", () -> startActivity(new Intent(this, InventoryActivity.class)));
                    } else if (reset) {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    btnLoadMore.setEnabled(true);
                    if (reset) {
                        adapter.clear();
                        adapter.notifyDataSetChanged();
                        state.showError(e.getMessage(), "重试", () -> load(true));
                    } else {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } finally {
                loading = false;
            }
        }).start();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
