package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
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

import java.util.ArrayList;
import java.util.List;

public class CreateRabbitActivity extends AppCompatActivity {
    private EditText etCageId;
    private Spinner spType;
    private Spinner spGender;
    private EditText etBreed;
    private Spinner spArrivalMethod;
    private EditText etArrivalDate;
    private EditText etWeight;
    private Button btnPickCage;
    private Button btnCreate;
    private TextView tvResult;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;

    private ApiClient api;
    private SessionStore session;
    private RecentStore recentStore;
    private PendingOpStore pendingStore;
    private final List<Long> cageIds = new ArrayList<Long>();
    private final List<String> cageLabels = new ArrayList<String>();
    private boolean posting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_rabbit);

        api = new ApiClient();
        session = new SessionStore(this);
        recentStore = new RecentStore(this);
        pendingStore = new PendingOpStore(this);

        etCageId = findViewById(R.id.etCageId);
        btnPickCage = findViewById(R.id.btnPickCage);
        spType = findViewById(R.id.spRabbitType);
        spGender = findViewById(R.id.spRabbitGender);
        etBreed = findViewById(R.id.etBreed);
        spArrivalMethod = findViewById(R.id.spArrivalMethod);
        etArrivalDate = findViewById(R.id.etArrivalDate);
        etWeight = findViewById(R.id.etWeight);
        btnCreate = findViewById(R.id.btnCreateRabbit);
        tvResult = findViewById(R.id.tvCreateRabbitResult);
        pb = findViewById(R.id.pbCreateRabbitLoading);
        state = new StatePanel(this);
        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);

        tvTopTitle.setText("录入兔子");
        btnTopBack.setOnClickListener(v -> finish());
        refreshHeader();
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{"种兔(0)", "后备(1)", "商品(2)"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(typeAdapter);
        spType.setSelection(0);

        ArrayAdapter<String> genderAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{"母(0)", "公(1)"});
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spGender.setAdapter(genderAdapter);
        spGender.setSelection(0);

        ArrayAdapter<String> arrivalAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{"购入(0)", "出生(1)"});
        arrivalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spArrivalMethod.setAdapter(arrivalAdapter);
        spArrivalMethod.setSelection(0);

        DatePickerUtil.attach(this, etArrivalDate);
        etCageId.setFocusable(false);
        etCageId.setClickable(true);
        etCageId.setOnClickListener(v -> pickCage());
        btnPickCage.setOnClickListener(v -> pickCage());
        btnCreate.setOnClickListener(v -> create());
    }

    private void refreshHeader() {
        long houseId = session.getHouseId();
        tvTopHouse.setText("用户：" + safe(session.getUserName()) + "  兔舍ID：" + (houseId <= 0 ? "未选择" : String.valueOf(houseId)));
    }

    private void pickCage() {
        if (!cageIds.isEmpty()) {
            showPickCageDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再选择笼位", "去登录", () -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再选择笼位", "关闭", this::finish);
            return;
        }
        tvResult.setText("加载笼位中...");
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/cages", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<Long> ids = new ArrayList<Long>();
                List<String> labels = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                    String code = o.has("code") && !o.get("code").isJsonNull() ? o.get("code").getAsString() : "";
                    if (id <= 0) {
                        continue;
                    }
                    ids.add(id);
                    labels.add("笼位#" + id + "  " + code);
                }
                runOnUiThread(() -> {
                    cageIds.clear();
                    cageLabels.clear();
                    cageIds.addAll(ids);
                    cageLabels.addAll(labels);
                    tvResult.setText("");
                    pb.setVisibility(android.view.View.GONE);
                    if (cageIds.isEmpty()) {
                        state.showEmpty("🧱", "暂无笼位", "先在“笼位维护”创建笼位，再回来录入兔子", "去笼位维护", () -> startActivity(new android.content.Intent(this, CagesManageActivity.class)));
                        return;
                    }
                    showPickCageDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", this::pickCage);
                });
            }
        }).start();
    }

    private void showPickCageDialog() {
        List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
        for (int i = 0; i < cageIds.size(); i++) {
            long id = cageIds.get(i);
            String label = i < cageLabels.size() ? cageLabels.get(i) : String.valueOf(id);
            items.add(new PickerDialogUtil.PickItem(id, label, label));
        }
        List<Long> recent = recentStore.getIds("cage_" + session.getHouseId());
        PickerDialogUtil.showSingle(this, "选择笼位", items, recent, it -> {
            if (it == null) {
                return;
            }
            etCageId.setText(String.valueOf(it.id));
            recentStore.push("cage_" + session.getHouseId(), it.id, 20);
            Toast.makeText(this, "已选择笼位", Toast.LENGTH_SHORT).show();
        });
    }

    private void create() {
        if (posting) {
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        long cageId = parseLong(etCageId.getText().toString().trim());
        String type = codeFromLabel(String.valueOf(spType.getSelectedItem()));
        String gender = codeFromLabel(String.valueOf(spGender.getSelectedItem()));
        String breed = etBreed.getText().toString().trim();
        String arrivalMethod = codeFromLabel(String.valueOf(spArrivalMethod.getSelectedItem()));
        String arrivalDate = etArrivalDate.getText().toString().trim();
        Double weight = parseDouble(etWeight.getText().toString().trim());
        java.util.Date ad = InputUtil.parseDate(arrivalDate);

        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再提交", "去登录", () -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再录入兔子", "关闭", this::finish);
            return;
        }
        if (cageId <= 0) {
            tvResult.setText("请选择笼位");
            return;
        }

        if (!arrivalDate.isEmpty() && ad == null) {
            tvResult.setText("日期格式错误：yyyy-MM-dd");
            return;
        }

        tvResult.setText("");
        posting = true;
        btnCreate.setEnabled(false);
        btnPickCage.setEnabled(false);
        pb.setVisibility(android.view.View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("cageId", cageId);
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                body.addProperty("type", type);
                body.addProperty("gender", gender);
                if (!breed.isEmpty()) {
                    body.addProperty("breed", breed);
                }
                body.addProperty("arrivalMethod", arrivalMethod);
                if (ad != null) {
                    body.addProperty("arrivalDate", ad.getTime());
                }
                if (weight != null) {
                    body.addProperty("weight", weight);
                }
                String bodyJson = body.toString();
                api.postJson("/api/rabbits", token, houseId, body);
                runOnUiThread(() -> {
                    tvResult.setText("录入成功");
                    pb.setVisibility(android.view.View.GONE);
                    posting = false;
                    finish();
                });
            } catch (Exception e) {
                JsonObject body = new JsonObject();
                body.addProperty("cageId", cageId);
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                body.addProperty("type", type);
                body.addProperty("gender", gender);
                if (!breed.isEmpty()) {
                    body.addProperty("breed", breed);
                }
                body.addProperty("arrivalMethod", arrivalMethod);
                if (ad != null) {
                    body.addProperty("arrivalDate", ad.getTime());
                }
                if (weight != null) {
                    body.addProperty("weight", weight);
                }
                String bodyJson = body.toString();
                PendingOp op = new PendingOp();
                op.setId(String.valueOf(System.currentTimeMillis()) + "-" + java.util.UUID.randomUUID().toString());
                op.setTitle("录入兔子");
                op.setPath("/api/rabbits");
                op.setHouseId(houseId);
                op.setCreateTime(System.currentTimeMillis());
                op.setBodyJson(bodyJson);
                pendingStore.add(op);
                runOnUiThread(() -> {
                    tvResult.setText(e.getMessage() + "（已存为待提交，可在“待提交”重试）");
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::create);
                    btnCreate.setEnabled(true);
                    btnPickCage.setEnabled(true);
                    posting = false;
                });
            }
        }).start();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String codeFromLabel(String label) {
        if (label == null) {
            return "";
        }
        int i = label.indexOf("(");
        int j = label.indexOf(")");
        if (i >= 0 && j > i) {
            return label.substring(i + 1, j);
        }
        return label.trim();
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return null;
        }
    }
}
