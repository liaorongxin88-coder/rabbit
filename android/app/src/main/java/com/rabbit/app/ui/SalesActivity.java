package com.rabbit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class SalesActivity extends AppCompatActivity {
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private TwoLineCardAdapter adapter;
    private final List<Long> saleIds = new ArrayList<Long>();

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
        tvTopTitle.setText("销售单");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.VISIBLE);
        btnTopRight.setText("操作");
        btnTopRight.setOnClickListener(v -> showActions());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= saleIds.size()) {
                return;
            }
            long saleId = saleIds.get(position);
            Intent it = new Intent(this, SaleDetailActivity.class);
            it.putExtra("saleId", saleId);
            startActivity(it);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void showActions() {
        String[] items = new String[]{"新增销售", "刷新"};
        new AlertDialog.Builder(this)
                .setTitle("销售单")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, SaleToolActivity.class));
                    } else if (which == 1) {
                        load();
                    }
                })
                .show();
    }

    private void load() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/sales?page=1&pageSize=100", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> rows = new ArrayList<String>();
                List<Long> ids = new ArrayList<Long>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsLong() : 0L;
                    String time = safeAny(o, "saleTime");
                    String customer = safeStr(o, "customer");
                    String totalWeight = safeAny(o, "totalWeight");
                    String unitPrice = safeAny(o, "unitPrice");
                    String totalAmount = safeAny(o, "totalAmount");
                    String remark = safeStr(o, "remark");
                    String title = "销售 " + TimeUtil.fmtAny(time) + "||sale:销售";
                    String sub = "客户：" + (customer.isEmpty() ? "-" : customer)
                            + "  总重：" + totalWeight
                            + "  单价：" + (unitPrice.isEmpty() ? "-" : unitPrice)
                            + "  金额：" + (totalAmount.isEmpty() ? "-" : totalAmount)
                            + (remark.isEmpty() ? "" : ("\n" + remark));
                    if (id > 0) {
                        ids.add(id);
                        rows.add(title + "\n" + sub);
                    }
                }
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    saleIds.clear();
                    saleIds.addAll(ids);
                    adapter.clear();
                    adapter.addAll(rows);
                    adapter.notifyDataSetChanged();
                    if (rows.isEmpty()) {
                        state.showEmpty("暂无销售单", "可以点右上角“新增销售”录入", "新增销售", v -> startActivity(new Intent(this, SaleToolActivity.class)));
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    saleIds.clear();
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    state.showError("加载失败", e.getMessage(), "重试", v -> load());
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

    private String safeAny(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        try {
            return o.get(key).getAsString();
        } catch (Exception ignored) {
            try {
                return String.valueOf(o.get(key).getAsLong());
            } catch (Exception e) {
                return "";
            }
        }
    }
}
