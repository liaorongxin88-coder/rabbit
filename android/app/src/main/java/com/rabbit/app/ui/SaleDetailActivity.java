package com.rabbit.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class SaleDetailActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private TextView tvSummary;
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;

    private ApiClient api;
    private SessionStore session;
    private TwoLineCardAdapter adapter;

    private long saleId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale_detail);

        api = new ApiClient();
        session = new SessionStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);

        tvSummary = findViewById(R.id.tvSaleSummary);
        lv = findViewById(R.id.lvSaleItems);
        pb = findViewById(R.id.pbSaleDetailLoading);
        state = new StatePanel(this);

        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        saleId = getIntent() == null ? 0L : getIntent().getLongExtra("saleId", 0L);
        tvTopTitle.setText("销售单详情");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (saleId <= 0) {
            state.showError("saleId缺失", "返回", v -> finish());
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/sales/" + saleId, token, houseId);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : null;
                if (data == null) {
                    throw new RuntimeException("empty data");
                }
                JsonObject order = data.has("order") && data.get("order").isJsonObject() ? data.getAsJsonObject("order") : new JsonObject();
                JsonArray items = data.has("items") && data.get("items").isJsonArray() ? data.getAsJsonArray("items") : new JsonArray();

                String saleTime = safeAny(order, "saleTime");
                String customer = safeStr(order, "customer");
                String totalWeight = safeAny(order, "totalWeight");
                String unitPrice = safeAny(order, "unitPrice");
                String totalAmount = safeAny(order, "totalAmount");
                String remark = safeStr(order, "remark");

                String summary = "销售单ID：" + saleId
                        + "\n时间：" + TimeUtil.fmtAny(saleTime)
                        + "\n客户：" + (customer.isEmpty() ? "-" : customer)
                        + "\n总重：" + totalWeight
                        + "\n单价：" + (unitPrice.isEmpty() ? "-" : unitPrice)
                        + "\n金额：" + (totalAmount.isEmpty() ? "-" : totalAmount)
                        + (remark.isEmpty() ? "" : ("\n备注：" + remark));

                List<String> rows = new ArrayList<String>();
                for (int i = 0; i < items.size(); i++) {
                    JsonObject it = items.get(i).getAsJsonObject();
                    String rabbitId = safeAny(it, "rabbitId");
                    String cageId = safeAny(it, "cageId");
                    String type = safeStr(it, "type");
                    String gender = safeStr(it, "gender");
                    String w = safeAny(it, "weight");
                    String p = safeAny(it, "price");
                    String title = "兔#" + rabbitId + "||sale:销售";
                    String sub = "笼位#" + cageId
                            + "  类型:" + type
                            + "  性别:" + gender
                            + (w.isEmpty() ? "" : ("  重量:" + w))
                            + (p.isEmpty() ? "" : ("  价格:" + p));
                    rows.add(title + "\n" + sub);
                }

                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    tvTopTitle.setText("销售单 " + saleId);
                    tvSummary.setText(summary);
                    adapter.clear();
                    adapter.addAll(rows);
                    adapter.notifyDataSetChanged();
                    if (rows.isEmpty()) {
                        state.showEmpty("暂无明细", "该销售单未包含兔子明细", "刷新", v -> load());
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

