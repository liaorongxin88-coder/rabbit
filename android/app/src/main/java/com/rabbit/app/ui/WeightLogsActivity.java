package com.rabbit.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.rabbit.app.storage.RecentStore;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.DatePickerUtil;
import com.rabbit.app.util.InputUtil;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WeightLogsActivity extends AppCompatActivity {
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;
    private RecentStore recentStore;
    private TwoLineCardAdapter adapter;

    private long selectedRabbitId;
    private final List<PickerDialogUtil.PickItem> rabbitItems = new ArrayList<PickerDialogUtil.PickItem>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        lv = findViewById(R.id.lvList);
        pb = findViewById(R.id.pbListLoading);
        state = new StatePanel(this);

        api = new ApiClient();
        session = new SessionStore(this);
        recentStore = new RecentStore(this);
        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);

        tvTopTitle.setText("体重记录");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.VISIBLE);
        btnTopRight.setText("操作");
        btnTopRight.setOnClickListener(v -> showActions());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());

        long rid = getIntent() == null ? 0L : getIntent().getLongExtra("rabbitId", 0L);
        if (rid > 0) {
            selectedRabbitId = rid;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void showActions() {
        String[] items = new String[]{"选择兔子", "新增记录", "刷新"};
        new AlertDialog.Builder(this)
                .setTitle("体重记录")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        pickRabbit();
                    } else if (which == 1) {
                        showAddDialog();
                    } else if (which == 2) {
                        load();
                    }
                })
                .show();
    }

    private void pickRabbit() {
        if (!rabbitItems.isEmpty()) {
            showPickRabbitDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/rabbits?active=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                    if (id <= 0) {
                        continue;
                    }
                    String type = safeStr(o, "type");
                    String cageId = safeStr(o, "cageId");
                    String label = "兔#" + id + "  类型:" + type + "\n笼位#" + cageId;
                    items.add(new PickerDialogUtil.PickItem(id, label, label));
                }
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    rabbitItems.clear();
                    rabbitItems.addAll(items);
                    if (rabbitItems.isEmpty()) {
                        state.showEmpty("暂无兔子", "先去“兔子”页面录入", "刷新", v -> pickRabbit());
                        return;
                    }
                    showPickRabbitDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    state.showError(e.getMessage(), "重试", v -> pickRabbit());
                });
            }
        }).start();
    }

    private void showPickRabbitDialog() {
        List<Long> recent = recentStore.getIds("weight_rabbit_" + session.getHouseId());
        PickerDialogUtil.showSingle(this, "选择兔子", rabbitItems, recent, it -> {
            if (it == null) {
                return;
            }
            selectedRabbitId = it.id;
            recentStore.push("weight_rabbit_" + session.getHouseId(), it.id, 50);
            load();
        });
    }

    private void showAddDialog() {
        if (selectedRabbitId <= 0) {
            Toast.makeText(this, "请先选择兔子", Toast.LENGTH_SHORT).show();
            pickRabbit();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        box.setPadding(pad, pad, pad, pad);

        EditText etDate = new EditText(this);
        etDate.setHint("称重日期");
        etDate.setText(com.rabbit.app.util.TimeUtil.today());
        DatePickerUtil.attach(this, etDate);
        box.addView(etDate);

        EditText etWeight = new EditText(this);
        etWeight.setHint("体重(kg)");
        etWeight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(etWeight);

        EditText etRemark = new EditText(this);
        etRemark.setHint("备注（可选）");
        box.addView(etRemark);

        new AlertDialog.Builder(this)
                .setTitle("新增体重记录（兔#" + selectedRabbitId + "）")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String ds = etDate.getText() == null ? "" : etDate.getText().toString().trim();
                    String ws = etWeight.getText() == null ? "" : etWeight.getText().toString().trim();
                    String remark = etRemark.getText() == null ? "" : etRemark.getText().toString().trim();
                    if (ws.isEmpty()) {
                        Toast.makeText(this, "体重不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double kg;
                    try {
                        kg = Double.parseDouble(ws);
                    } catch (Exception e) {
                        Toast.makeText(this, "体重不合法", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    java.util.Date dt = InputUtil.parseDate(ds);
                    if (dt == null) {
                        Toast.makeText(this, "日期不合法", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    doAdd(dt.getTime(), kg, remark);
                })
                .show();
    }

    private void doAdd(long weighTimeMs, double kg, String remark) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("rabbitId", selectedRabbitId);
                body.addProperty("weighTime", weighTimeMs);
                body.addProperty("weightKg", kg);
                if (remark != null && !remark.isEmpty()) {
                    body.addProperty("remark", remark);
                }
                body.addProperty("requestId", UUID.randomUUID().toString());
                api.postJson("/api/weight-logs", token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    load();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void load() {
        if (selectedRabbitId <= 0) {
            tvTopTitle.setText("体重记录");
            adapter.clear();
            adapter.notifyDataSetChanged();
            state.showEmpty("未选择兔子", "先选择一只兔子，再查看体重曲线", "选择兔子", v -> pickRabbit());
            return;
        }
        tvTopTitle.setText("体重记录 兔#" + selectedRabbitId);
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/weight-logs?rabbitId=" + selectedRabbitId + "&limit=100", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> rows = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    String time = safeAny(o, "weighTime");
                    String kg = safeAny(o, "weightKg");
                    String remark = safeStr(o, "remark");
                    String t = "称重 " + TimeUtil.fmtAny(time) + "||feed:体重";
                    String sub = "体重 " + kg + " kg" + (remark.isEmpty() ? "" : ("\n" + remark));
                    rows.add(t + "\n" + sub);
                }
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    adapter.clear();
                    adapter.addAll(rows);
                    adapter.notifyDataSetChanged();
                    if (rows.isEmpty()) {
                        state.showEmpty("暂无记录", "可以点右上角“操作→新增记录”", "新增", v -> showAddDialog());
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

