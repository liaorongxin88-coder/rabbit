package com.rabbit.app.ui;

import android.os.Bundle;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class RabbitsActivity extends AppCompatActivity {
    private ListView lv;
    private EditText etSearch;
    private Button btnRefresh;
    private Button btnAdd;
    private Button btnLoadMore;
    private ProgressBar pb;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private ArrayAdapter<String> adapter;
    private StatePanel state;
    private final List<Long> rabbitIds = new ArrayList<Long>();
    private final List<Long> allRabbitIds = new ArrayList<Long>();
    private final List<String> allItems = new ArrayList<String>();
    private String query = "";
    private long cageId = 0L;
    private String cageNumber = null;
    private int page = 1;
    private final int pageSize = 50;
    private boolean hasMore = true;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rabbits);

        lv = findViewById(R.id.lvRabbits);
        etSearch = findViewById(R.id.etRabbitSearch);
        btnRefresh = findViewById(R.id.btnRefreshRabbits);
        btnAdd = findViewById(R.id.btnAddRabbit);
        btnLoadMore = findViewById(R.id.btnLoadMoreRabbits);
        pb = findViewById(R.id.pbRabbitsLoading);

        api = new ApiClient();
        session = new SessionStore(this);
        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);
        state = new StatePanel(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);

        cageId = getIntent().getLongExtra("cageId", 0L);
        cageNumber = getIntent().getStringExtra("cageNumber");
        if (cageId > 0) {
            tvTopTitle.setText("兔子");
            tvTopHouse.setText("兔舍ID：" + session.getHouseId() + "  ·  笼位 " + (cageNumber == null ? "" : cageNumber));
        } else {
            tvTopTitle.setText("兔子");
            tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        }
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        btnRefresh.setOnClickListener(v -> load(true));
        btnAdd.setOnClickListener(v -> startActivity(new Intent(this, CreateRabbitActivity.class)));
        btnLoadMore.setOnClickListener(v -> load(false));

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < rabbitIds.size()) {
                long rabbitId = rabbitIds.get(position);
                Intent it = new Intent(this, RabbitStatusHistoryActivity.class);
                it.putExtra("rabbitId", rabbitId);
                startActivity(it);
            }
        });

        lv.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < rabbitIds.size()) {
                long rabbitId = rabbitIds.get(position);
                Intent it = new Intent(this, RabbitEditActivity.class);
                it.putExtra("rabbitId", rabbitId);
                startActivity(it);
                return true;
            }
            return false;
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString().trim();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(true);
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
        if (houseId <= 0) {
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再加载兔子列表", "刷新", () -> load(true));
            return;
        }
        if (reset) {
            page = 1;
            hasMore = true;
            allItems.clear();
            allRabbitIds.clear();
            applyFilter();
        }
        int requestPage = page;
        loading = true;
        runOnUiThread(() -> {
            btnLoadMore.setEnabled(false);
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                String url = "/api/rabbits?active=true";
                if (cageId > 0) {
                    url += "&cageId=" + cageId;
                }
                url += "&page=" + requestPage + "&pageSize=" + pageSize;
                JsonObject resp = api.getJson(url, token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.get("id").getAsLong();
                    String type = o.has("type") && !o.get("type").isJsonNull() ? o.get("type").getAsString() : "";
                    String gender = o.has("gender") && !o.get("gender").isJsonNull() ? o.get("gender").getAsString() : "";
                    long cage = o.has("cageId") && !o.get("cageId").isJsonNull() ? o.get("cageId").getAsLong() : 0L;
                    allRabbitIds.add(id);
                    allItems.add("兔#" + id + "  类型:" + typeLabel(type) + "  性别:" + genderLabel(gender) + "\n笼位#" + cage + "（点开看状态历史）");
                }
                hasMore = arr.size() >= pageSize;
                if (!reset) {
                    page = page + 1;
                } else {
                    page = 2;
                }
                runOnUiThread(() -> {
                    applyFilter();
                    btnLoadMore.setEnabled(hasMore);
                    btnLoadMore.setText(hasMore ? "加载更多" : "没有更多了");
                    pb.setVisibility(android.view.View.GONE);
                    if (allItems.isEmpty()) {
                        state.showEmpty("🐇", "暂无兔子", "可以点击“录入兔子”开始使用", "刷新", () -> load(true));
                    } else if (!query.isEmpty() && adapter.getCount() == 0) {
                        state.showEmpty("🔎", "没有匹配结果", "换个关键词试试", "清空搜索", () -> etSearch.setText(""));
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    btnLoadMore.setEnabled(true);
                    state.showError(e.getMessage(), "重试", () -> load(requestPage == 1));
                });
            } finally {
                loading = false;
            }
        }).start();
    }

    private void applyFilter() {
        String q = query == null ? "" : query.trim().toLowerCase();
        rabbitIds.clear();
        adapter.clear();
        for (int i = 0; i < allItems.size(); i++) {
            String s = allItems.get(i);
            if (q.isEmpty() || (s != null && s.toLowerCase().contains(q))) {
                adapter.add(s);
                rabbitIds.add(allRabbitIds.get(i));
            }
        }
        adapter.notifyDataSetChanged();
        if (state != null && pb != null && pb.getVisibility() != android.view.View.VISIBLE) {
            if (!q.isEmpty() && !allItems.isEmpty() && adapter.getCount() == 0) {
                state.showEmpty("🔎", "没有匹配结果", "换个关键词试试", "清空搜索", () -> etSearch.setText(""));
            } else if (!allItems.isEmpty()) {
                state.hide();
            }
        }
    }

    private String typeLabel(String t) {
        if ("0".equals(t)) {
            return "种兔";
        }
        if ("1".equals(t)) {
            return "后备兔";
        }
        if ("2".equals(t)) {
            return "商品兔";
        }
        return t == null ? "" : t;
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
