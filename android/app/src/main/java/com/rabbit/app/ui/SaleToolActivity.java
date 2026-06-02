package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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
import com.rabbit.app.util.DatePickerUtil;
import com.rabbit.app.util.InputUtil;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class SaleToolActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private TextView tvSelected;
    private Button btnPick;
    private EditText etDate;
    private EditText etTotalWeight;
    private EditText etUnitPrice;
    private EditText etCustomer;
    private EditText etRemark;
    private Button btnSubmit;
    private TextView tvResult;
    private ProgressBar pb;
    private StatePanel state;

    private ApiClient api;
    private SessionStore session;
    private RecentStore recentStore;
    private boolean posting;

    private final List<Long> rabbitIds = new ArrayList<Long>();
    private final List<String> rabbitLabels = new ArrayList<String>();
    private final List<Long> selected = new ArrayList<Long>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale_tool);

        api = new ApiClient();
        session = new SessionStore(this);
        recentStore = new RecentStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("销售出栏");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        tvSelected = findViewById(R.id.tvSaleSelected);
        btnPick = findViewById(R.id.btnPickSaleRabbits);
        etDate = findViewById(R.id.etSaleDate);
        etTotalWeight = findViewById(R.id.etSaleTotalWeight);
        etUnitPrice = findViewById(R.id.etSaleUnitPrice);
        etCustomer = findViewById(R.id.etSaleCustomer);
        etRemark = findViewById(R.id.etSaleRemark);
        btnSubmit = findViewById(R.id.btnSubmitSale);
        tvResult = findViewById(R.id.tvSaleResult);
        pb = findViewById(R.id.pbSaleLoading);
        state = new StatePanel(this);

        etDate.setText(TimeUtil.today());
        DatePickerUtil.attach(this, etDate);

        btnPick.setOnClickListener(v -> pickRabbits());
        btnSubmit.setOnClickListener(v -> submit());
        refreshSelectedText();
    }

    private void pickRabbits() {
        if (!rabbitIds.isEmpty()) {
            showPickDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再选择出栏兔", "刷新", this::pickRabbits);
            return;
        }
        tvResult.setText("加载可出栏兔子中...");
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/rabbits?type=2&active=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<Long> ids = new ArrayList<Long>();
                List<String> labels = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                    String cageId = o.has("cageId") && !o.get("cageId").isJsonNull() ? o.get("cageId").getAsString() : "";
                    String weight = o.has("weight") && !o.get("weight").isJsonNull() ? o.get("weight").getAsString() : "";
                    if (id <= 0) {
                        continue;
                    }
                    ids.add(id);
                    labels.add("兔#" + id + "  体重:" + safe(weight) + "\n笼位#" + cageId);
                }
                runOnUiThread(() -> {
                    rabbitIds.clear();
                    rabbitLabels.clear();
                    rabbitIds.addAll(ids);
                    rabbitLabels.addAll(labels);
                    tvResult.setText("");
                    pb.setVisibility(android.view.View.GONE);
                    if (rabbitIds.isEmpty()) {
                        state.showEmpty("🐇", "暂无可出栏商品兔", "没有找到 type=2, active=true 的兔子", "刷新", this::pickRabbits);
                        return;
                    }
                    showPickDialog();
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

    private void showPickDialog() {
        List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
        for (int i = 0; i < rabbitIds.size(); i++) {
            long id = rabbitIds.get(i);
            String label = i < rabbitLabels.size() ? rabbitLabels.get(i) : String.valueOf(id);
            items.add(new PickerDialogUtil.PickItem(id, label, label));
        }
        List<Long> recent = recentStore.getIds("sale_rabbit_" + session.getHouseId());
        PickerDialogUtil.showMulti(this, "选择出栏兔（多选）", items, recent, selected, picked -> {
            selected.clear();
            if (picked != null) {
                for (PickerDialogUtil.PickItem it : picked) {
                    selected.add(it.id);
                    recentStore.push("sale_rabbit_" + session.getHouseId(), it.id, 50);
                }
            }
            refreshSelectedText();
        });
    }

    private void refreshSelectedText() {
        if (selected.isEmpty()) {
            tvSelected.setText("未选择兔子");
            return;
        }
        tvSelected.setText("已选：" + selected.size() + " 只（提交后自动出栏离场）");
    }

    private void submit() {
        if (posting) {
            return;
        }
        if (selected.isEmpty()) {
            tvResult.setText("请先选择出栏兔");
            return;
        }
        java.util.Date d = InputUtil.parseDate(etDate.getText().toString().trim());
        if (d == null) {
            tvResult.setText("日期格式错误：yyyy-MM-dd");
            etDate.setError("日期格式错误：yyyy-MM-dd");
            return;
        }
        Double totalWeight = parseDouble(etTotalWeight.getText().toString().trim());
        if (totalWeight == null || totalWeight <= 0) {
            tvResult.setText("请填写总重(kg)");
            etTotalWeight.setError("必填");
            return;
        }
        Double unitPrice = parseDouble(etUnitPrice.getText().toString().trim());
        String customer = etCustomer.getText().toString().trim();
        String remark = etRemark.getText().toString().trim();

        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Long id : selected) {
            arr.add(id);
        }
        body.add("rabbitIds", arr);
        body.addProperty("saleTime", d.getTime());
        body.addProperty("totalWeight", totalWeight);
        if (unitPrice != null) {
            body.addProperty("unitPrice", unitPrice);
        }
        if (!customer.isEmpty()) {
            body.addProperty("customer", customer);
        }
        if (!remark.isEmpty()) {
            body.addProperty("remark", remark);
        }
        body.addProperty("requestId", java.util.UUID.randomUUID().toString());

        String token = session.getToken();
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再提交销售出栏", "刷新", this::submit);
            return;
        }
        posting = true;
        btnSubmit.setEnabled(false);
        btnPick.setEnabled(false);
        tvResult.setText("处理中...");
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.postJson("/api/sales", token, houseId, body);
                long orderId = resp.has("data") && resp.get("data").isJsonObject() && resp.getAsJsonObject("data").has("id") ? resp.getAsJsonObject("data").get("id").getAsLong() : 0L;
                runOnUiThread(() -> {
                    tvResult.setText("提交成功：销售单#" + orderId + "  数量:" + selected.size() + "  总重:" + totalWeight);
                    Toast.makeText(this, "提交成功", Toast.LENGTH_SHORT).show();
                    selected.clear();
                    refreshSelectedText();
                    pb.setVisibility(android.view.View.GONE);
                    btnSubmit.setEnabled(true);
                    btnPick.setEnabled(true);
                    posting = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvResult.setText("");
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::submit);
                    btnSubmit.setEnabled(true);
                    btnPick.setEnabled(true);
                    posting = false;
                });
            }
        }).start();
    }

    private Double parseDouble(String s) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(t);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
