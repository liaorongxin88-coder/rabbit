package com.rabbit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

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
import java.util.List;

public class BatchOpsActivity extends AppCompatActivity {
    private ScrollView sv;
    private Button btnLoadBatchRabbits;
    private ListView lvBreeding;
    private ListView lvFattening;
    private Button btnAphStart;
    private Button btnAphFinish;

    private EditText etMaleId;
    private EditText etMatingDate;
    private Button btnMating;
    private Button btnPickMale;

    private EditText etCheckDate;
    private Spinner spCheckResult;
    private Button btnPregCheck;

    private EditText etPrepartumDate;
    private Button btnPrepartumFinish;

    private EditText etBirthDate;
    private EditText etTotalKits;
    private EditText etLiveKits;
    private CheckBox cbFailed;
    private Button btnParturition;

    private EditText etWeaningDate;
    private EditText etWeaningCount;
    private EditText etMaleCount;
    private EditText etFemaleCount;
    private EditText etWeaningTargetCage;
    private Button btnPickWeaningTargetCage;
    private Button btnWeaning;

    private EditText etSaleDate;
    private Button btnSale;

    private EditText etBatchEndDate;
    private CheckBox cbForceCompleteBatch;
    private Button btnCompleteBatch;

    private TextView tvNextHint;
    private TextView tvSelected;

    private TextView tvResult;
    private ProgressBar pb;
    private StatePanel state;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private long batchId;
    private ApiClient api;
    private SessionStore session;
    private PendingOpStore pendingStore;
    private RecentStore recentStore;
    private ArrayAdapter<String> breedingAdapter;
    private ArrayAdapter<String> fatteningAdapter;
    private final List<Long> breedingRabbitIds = new ArrayList<Long>();
    private final List<String> breedingStatuses = new ArrayList<String>();
    private final List<String> breedingNextTypes = new ArrayList<String>();
    private final List<Long> breedingNextDatesMs = new ArrayList<Long>();
    private final List<Boolean> breedingDue = new ArrayList<Boolean>();
    private final List<Long> fatteningRabbitIds = new ArrayList<Long>();
    private final List<Boolean> fatteningDue = new ArrayList<Boolean>();
    private final List<String> fatteningNextTypes = new ArrayList<String>();
    private final List<Long> fatteningNextDatesMs = new ArrayList<Long>();

    private final List<Long> maleRabbitIds = new ArrayList<Long>();
    private final List<String> maleRabbitLabels = new ArrayList<String>();

    private Long weaningTargetCageId;
    private final List<Long> weaningCageIds = new ArrayList<Long>();
    private final List<String> weaningCageLabels = new ArrayList<String>();

    private boolean posting = false;
    private long preselectRabbitId;
    private String preselectEventType;
    private String fromEventCategory;
    private long fromEventRecordId;
    private String lastSuggestedType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_ops);

        batchId = getIntent().getLongExtra("batchId", 0L);
        preselectRabbitId = getIntent().getLongExtra("rabbitId", 0L);
        preselectEventType = getIntent().getStringExtra("eventType");
        fromEventCategory = getIntent().getStringExtra("eventCategory");
        fromEventRecordId = getIntent().getLongExtra("eventRecordId", 0L);
        api = new ApiClient();
        session = new SessionStore(this);
        pendingStore = new PendingOpStore(this);
        recentStore = new RecentStore(this);

        sv = findViewById(R.id.svBatchOps);
        btnLoadBatchRabbits = findViewById(R.id.btnLoadBatchRabbits);
        lvBreeding = findViewById(R.id.lvBreeding);
        lvFattening = findViewById(R.id.lvFattening);
        btnAphStart = findViewById(R.id.btnAphStart);
        btnAphFinish = findViewById(R.id.btnAphFinish);

        etMaleId = findViewById(R.id.etMaleId);
        etMatingDate = findViewById(R.id.etMatingDate);
        btnMating = findViewById(R.id.btnMating);
        btnPickMale = findViewById(R.id.btnPickMale);

        etCheckDate = findViewById(R.id.etCheckDate);
        spCheckResult = findViewById(R.id.spCheckResult);
        btnPregCheck = findViewById(R.id.btnPregCheck);

        etPrepartumDate = findViewById(R.id.etPrepartumDate);
        btnPrepartumFinish = findViewById(R.id.btnPrepartumFinish);

        etBirthDate = findViewById(R.id.etBirthDate);
        etTotalKits = findViewById(R.id.etTotalKits);
        etLiveKits = findViewById(R.id.etLiveKits);
        cbFailed = findViewById(R.id.cbFailed);
        btnParturition = findViewById(R.id.btnParturition);

        etWeaningDate = findViewById(R.id.etWeaningDate);
        etWeaningCount = findViewById(R.id.etWeaningCount);
        etMaleCount = findViewById(R.id.etWeaningMaleCount);
        etFemaleCount = findViewById(R.id.etWeaningFemaleCount);
        etWeaningTargetCage = findViewById(R.id.etWeaningTargetCage);
        btnPickWeaningTargetCage = findViewById(R.id.btnPickWeaningTargetCage);
        btnWeaning = findViewById(R.id.btnWeaning);

        etSaleDate = findViewById(R.id.etSaleDate);
        btnSale = findViewById(R.id.btnSale);

        etBatchEndDate = findViewById(R.id.etBatchEndDate);
        cbForceCompleteBatch = findViewById(R.id.cbForceCompleteBatch);
        btnCompleteBatch = findViewById(R.id.btnCompleteBatch);

        tvNextHint = findViewById(R.id.tvBatchNextHint);
        tvSelected = findViewById(R.id.tvBatchSelected);
        tvResult = findViewById(R.id.tvBatchOpsResult);
        pb = findViewById(R.id.pbBatchOpsLoading);
        state = new StatePanel(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);

        tvTopTitle.setText("批次操作");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId() + "  批次#" + batchId);
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, newHouseId -> {
            android.content.Intent it = new android.content.Intent(this, MainActivity.class);
            it.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            finish();
        });

        breedingAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<String>()) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getView(position, convertView, parent);
                if (v instanceof CheckedTextView) {
                    CheckedTextView tv = (CheckedTextView) v;
                    boolean due = position >= 0 && position < breedingDue.size() && Boolean.TRUE.equals(breedingDue.get(position));
                    tv.setTextColor(due ? 0xFFD32F2F : 0xFF000000);
                }
                return v;
            }
        };
        lvBreeding.setAdapter(breedingAdapter);
        lvBreeding.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        fatteningAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<String>()) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getView(position, convertView, parent);
                if (v instanceof CheckedTextView) {
                    CheckedTextView tv = (CheckedTextView) v;
                    boolean due = position >= 0 && position < fatteningDue.size() && Boolean.TRUE.equals(fatteningDue.get(position));
                    tv.setTextColor(due ? 0xFFD32F2F : 0xFF000000);
                }
                return v;
            }
        };
        lvFattening.setAdapter(fatteningAdapter);
        lvFattening.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        ArrayAdapter<String> checkResultAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{"怀孕", "空怀", "不确定"});
        checkResultAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCheckResult.setAdapter(checkResultAdapter);

        DatePickerUtil.attach(this, etMatingDate);
        DatePickerUtil.attach(this, etCheckDate);
        DatePickerUtil.attach(this, etPrepartumDate);
        DatePickerUtil.attach(this, etBirthDate);
        DatePickerUtil.attach(this, etWeaningDate);
        DatePickerUtil.attach(this, etSaleDate);
        DatePickerUtil.attach(this, etBatchEndDate);

        etMaleId.setFocusable(false);
        etMaleId.setClickable(true);
        etMaleId.setOnClickListener(v -> pickMale());

        etWeaningTargetCage.setFocusable(false);
        etWeaningTargetCage.setClickable(true);
        etWeaningTargetCage.setText("自动（系统选择）");
        weaningTargetCageId = null;
        etWeaningTargetCage.setOnClickListener(v -> pickWeaningTargetCage());
        btnPickWeaningTargetCage.setOnClickListener(v -> pickWeaningTargetCage());

        btnLoadBatchRabbits.setOnClickListener(v -> loadBatchRabbits());
        btnAphStart.setOnClickListener(v -> aphrodisiac(true));
        btnAphFinish.setOnClickListener(v -> aphrodisiac(false));
        btnMating.setOnClickListener(v -> mating());
        btnPickMale.setOnClickListener(v -> pickMale());
        btnPregCheck.setOnClickListener(v -> pregCheck());
        btnPrepartumFinish.setOnClickListener(v -> prepartumFinish());
        btnParturition.setOnClickListener(v -> parturition());
        btnWeaning.setOnClickListener(v -> weaning());
        btnSale.setOnClickListener(v -> sale());
        btnCompleteBatch.setOnClickListener(v -> completeBatch());

        tvNextHint.setOnClickListener(v -> {
            if (lastSuggestedType != null && !lastSuggestedType.trim().isEmpty()) {
                fillDateForNextType(lastSuggestedType);
                fillMaleForNextType(lastSuggestedType);
                scrollToEventType(lastSuggestedType);
            }
        });

        lvBreeding.setOnItemClickListener((parent, view, position, id) -> {
            updateButtonState();
            updateNextHint();
            if (position >= 0 && position < breedingDue.size() && Boolean.TRUE.equals(breedingDue.get(position))) {
                String nextType = position < breedingNextTypes.size() ? breedingNextTypes.get(position) : null;
                fillDateForNextType(nextType);
                fillMaleForNextType(nextType);
            }
        });

        lvFattening.setOnItemClickListener((parent, view, position, id) -> {
            updateButtonState();
            updateNextHint();
            if (position >= 0 && position < fatteningDue.size() && Boolean.TRUE.equals(fatteningDue.get(position))) {
                String nextType = position < fatteningNextTypes.size() ? fatteningNextTypes.get(position) : null;
                fillDateForNextType(nextType);
            }
        });

        updateButtonState();
        updateNextHint();
        loadBatchRabbits();
    }

    private void loadBatchRabbits() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            if (pb != null) {
                pb.setVisibility(android.view.View.GONE);
            }
            state.showEmpty("🔒", "未登录", "请先登录后再查看批次兔", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            if (pb != null) {
                pb.setVisibility(android.view.View.GONE);
            }
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再查看批次兔", "关闭", this::finish);
            return;
        }
        tvResult.setText("");
        if (pb != null) {
            pb.setVisibility(android.view.View.VISIBLE);
        }
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/batches/" + batchId + "/batch-rabbits?active=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> breeding = new ArrayList<String>();
                List<String> fattening = new ArrayList<String>();
                breedingRabbitIds.clear();
                breedingStatuses.clear();
                breedingNextTypes.clear();
                breedingNextDatesMs.clear();
                breedingDue.clear();
                fatteningRabbitIds.clear();
                fatteningDue.clear();
                fatteningNextTypes.clear();
                fatteningNextDatesMs.clear();

                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    String role = o.has("batchRole") ? o.get("batchRole").getAsString() : "";
                    long rabbitId = o.get("rabbitId").getAsLong();
                    String status = o.has("currentStatus") ? o.get("currentStatus").getAsString() : "";
                    String nextType = o.has("nextEventType") && !o.get("nextEventType").isJsonNull() ? o.get("nextEventType").getAsString() : "";
                    Long nextMs = parseMillis(o, "nextEventDate");
                    long now = System.currentTimeMillis();
                    boolean due = nextMs != null && nextMs > 0 && nextMs <= now;
                    String nextDate = nextMs == null ? "" : TimeUtil.fmt(nextMs);
                    String dueText = formatDueText(nextMs, now);
                    String line1 = (dueText.isEmpty() ? "" : dueText + "  ") + "兔#" + rabbitId + "  状态:" + safe(status);
                    String line2 = "下一步:" + safe(nextType) + "  " + safe(nextDate);
                    String line = line1 + "\n" + line2;
                    if ("breeding".equals(role)) {
                        breedingRabbitIds.add(rabbitId);
                        breedingStatuses.add(status);
                        breedingNextTypes.add(nextType);
                        breedingNextDatesMs.add(nextMs);
                        breedingDue.add(due);
                        breeding.add(line);
                    } else if ("fattening".equals(role)) {
                        fatteningRabbitIds.add(rabbitId);
                        fatteningDue.add(due);
                        fatteningNextTypes.add(nextType);
                        fatteningNextDatesMs.add(nextMs);
                        fattening.add(line);
                    }
                }
                runOnUiThread(() -> {
                    breedingAdapter.clear();
                    breedingAdapter.addAll(breeding);
                    breedingAdapter.notifyDataSetChanged();
                    fatteningAdapter.clear();
                    fatteningAdapter.addAll(fattening);
                    fatteningAdapter.notifyDataSetChanged();
                    clearSelections();
                    autoSelectFromExtras();
                    updateButtonState();
                    updateNextHint();
                    if (pb != null) {
                        pb.setVisibility(android.view.View.GONE);
                    }
                    state.hide();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (pb != null) {
                        pb.setVisibility(android.view.View.GONE);
                    }
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", this::loadBatchRabbits);
                    if (String.valueOf(e.getMessage()).contains("403") || String.valueOf(e.getMessage()).contains("404")) {
                        new AlertDialog.Builder(this)
                                .setTitle("批次不可用")
                                .setMessage("当前兔舍下无法访问该批次，建议切换回对应兔舍或返回上一页")
                                .setPositiveButton("返回", (d, which) -> finish())
                                .setNegativeButton("取消", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private void clearSelections() {
        for (int i = 0; i < breedingAdapter.getCount(); i++) {
            lvBreeding.setItemChecked(i, false);
        }
        for (int i = 0; i < fatteningAdapter.getCount(); i++) {
            lvFattening.setItemChecked(i, false);
        }
        updateNextHint();
    }

    private void aphrodisiac(boolean start) {
        List<Long> ids = getCheckedIds(lvBreeding, breedingRabbitIds);
        if (ids.isEmpty()) {
            tvResult.setText("请先在繁殖母兔列表勾选兔子");
            return;
        }
        JsonArray arr = new JsonArray();
        for (Long id : ids) {
            arr.add(id);
        }
        JsonObject body = new JsonObject();
        body.add("rabbitIds", arr);
        String path = start ? "/api/batches/" + batchId + "/aphrodisiac/start" : "/api/batches/" + batchId + "/aphrodisiac/finish";
        post(path, body);
    }

    private void mating() {
        Long femaleId = getSingleCheckedId(lvBreeding, breedingRabbitIds);
        if (femaleId == null) {
            failOnList(lvBreeding, "请在繁殖母兔列表勾选一只母兔");
            return;
        }
        long maleId = parseLong(etMaleId.getText().toString().trim());
        if (maleId <= 0) {
            fail(etMaleId, "请选择公兔");
            return;
        }
        java.util.Date d = InputUtil.parseDate(etMatingDate.getText().toString().trim());
        if (d == null) {
            fail(etMatingDate, "日期格式错误：yyyy-MM-dd");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("femaleRabbitId", femaleId);
        body.addProperty("maleRabbitId", maleId);
        body.addProperty("matingDate", d.getTime());
        preselectRabbitId = femaleId;
        preselectEventType = null;
        post("/api/batches/" + batchId + "/mating", body);
    }

    private void pregCheck() {
        Long rid = getSingleCheckedId(lvBreeding, breedingRabbitIds);
        if (rid == null) {
            failOnList(lvBreeding, "请在繁殖母兔列表勾选一只母兔");
            return;
        }
        java.util.Date d = InputUtil.parseDate(etCheckDate.getText().toString().trim());
        if (d == null) {
            fail(etCheckDate, "日期格式错误：yyyy-MM-dd");
            return;
        }
        Object selected = spCheckResult.getSelectedItem();
        String result = selected == null ? "" : String.valueOf(selected).trim();
        if (result.isEmpty()) {
            tvResult.setText("请选择摸胎结果");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("rabbitId", rid);
        body.addProperty("checkDate", d.getTime());
        body.addProperty("result", result);
        preselectRabbitId = rid;
        preselectEventType = null;
        post("/api/batches/" + batchId + "/pregnancy-check", body);
    }

    private void prepartumFinish() {
        Long rid = getSingleCheckedId(lvBreeding, breedingRabbitIds);
        if (rid == null) {
            failOnList(lvBreeding, "请在繁殖母兔列表勾选一只母兔");
            return;
        }
        java.util.Date d = InputUtil.parseDate(etPrepartumDate.getText().toString().trim());
        if (d == null) {
            fail(etPrepartumDate, "日期格式错误：yyyy-MM-dd");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("rabbitId", rid);
        body.addProperty("actionDate", d.getTime());
        preselectRabbitId = rid;
        preselectEventType = null;
        post("/api/batches/" + batchId + "/prepartum/finish", body);
    }

    private void parturition() {
        Long rid = getSingleCheckedId(lvBreeding, breedingRabbitIds);
        if (rid == null) {
            failOnList(lvBreeding, "请在繁殖母兔列表勾选一只母兔");
            return;
        }
        java.util.Date d = InputUtil.parseDate(etBirthDate.getText().toString().trim());
        if (d == null) {
            fail(etBirthDate, "日期格式错误：yyyy-MM-dd");
            return;
        }
        boolean failed = cbFailed.isChecked();
        String totalStr = etTotalKits.getText().toString().trim();
        String liveStr = etLiveKits.getText().toString().trim();
        if (!failed) {
            if (totalStr.isEmpty() || liveStr.isEmpty()) {
                if (totalStr.isEmpty()) {
                    fail(etTotalKits, "请填写 totalKits");
                } else {
                    fail(etLiveKits, "请填写 liveKits");
                }
                return;
            }
        }
        int total = parseInt(totalStr);
        int live = parseInt(liveStr);
        if (total < 0 || live < 0) {
            tvResult.setText("数量不能为负数");
            return;
        }
        if (live > total) {
            fail(etLiveKits, "liveKits 不能大于 totalKits");
            return;
        }
        if (!failed && total == 0) {
            fail(etTotalKits, "totalKits 必须大于0");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("rabbitId", rid);
        body.addProperty("birthDate", d.getTime());
        body.addProperty("totalKits", total);
        body.addProperty("liveKits", live);
        body.addProperty("failed", failed);
        preselectRabbitId = rid;
        preselectEventType = null;
        post("/api/batches/" + batchId + "/parturition", body);
    }

    private void weaning() {
        Long rid = getSingleCheckedId(lvBreeding, breedingRabbitIds);
        if (rid == null) {
            failOnList(lvBreeding, "请在繁殖母兔列表勾选一只母兔");
            return;
        }
        java.util.Date d = InputUtil.parseDate(etWeaningDate.getText().toString().trim());
        if (d == null) {
            fail(etWeaningDate, "日期格式错误：yyyy-MM-dd");
            return;
        }
        String countStr = etWeaningCount.getText().toString().trim();
        if (countStr.isEmpty()) {
            fail(etWeaningCount, "请填写 weaningCount");
            return;
        }
        int count = parseInt(countStr);
        if (count < 0) {
            fail(etWeaningCount, "weaningCount 不能为负数");
            return;
        }
        Integer male = parseNullableInt(etMaleCount.getText().toString().trim());
        Integer female = parseNullableInt(etFemaleCount.getText().toString().trim());
        if (male != null && male < 0) {
            fail(etMaleCount, "maleCount 不能为负数");
            return;
        }
        if (female != null && female < 0) {
            fail(etFemaleCount, "femaleCount 不能为负数");
            return;
        }
        if (male != null || female != null) {
            int m = male == null ? 0 : male;
            int f = female == null ? 0 : female;
            if (m + f != count) {
                tvResult.setText("maleCount + femaleCount 必须等于 weaningCount");
                return;
            }
        }
        JsonObject body = new JsonObject();
        body.addProperty("rabbitId", rid);
        body.addProperty("weaningDate", d.getTime());
        body.addProperty("weaningCount", count);
        if (weaningTargetCageId != null && weaningTargetCageId > 0) {
            body.addProperty("targetCageId", weaningTargetCageId);
        }
        if (male != null) {
            body.addProperty("maleCount", male);
        }
        if (female != null) {
            body.addProperty("femaleCount", female);
        }
        preselectRabbitId = rid;
        preselectEventType = null;
        post("/api/batches/" + batchId + "/weaning", body);
    }

    private void pickWeaningTargetCage() {
        if (!weaningCageIds.isEmpty()) {
            showPickWeaningTargetCageDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再选择目标笼位", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再选择目标笼位", "关闭", this::finish);
            return;
        }
        tvResult.setText("加载笼位中...");
        if (pb != null) {
            pb.setVisibility(android.view.View.VISIBLE);
        }
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
                    String num = o.has("cageNumber") && !o.get("cageNumber").isJsonNull() ? o.get("cageNumber").getAsString() : "";
                    String status = o.has("status") && !o.get("status").isJsonNull() ? o.get("status").getAsString() : "";
                    boolean enabled = !o.has("isEnabled") || o.get("isEnabled").isJsonNull() || o.get("isEnabled").getAsBoolean();
                    if (id <= 0) {
                        continue;
                    }
                    if (!enabled) {
                        continue;
                    }
                    if (!"0".equals(status) && !"3".equals(status)) {
                        continue;
                    }
                    ids.add(id);
                    labels.add("笼位#" + id + "  " + num);
                }
                runOnUiThread(() -> {
                    weaningCageIds.clear();
                    weaningCageLabels.clear();
                    weaningCageIds.addAll(ids);
                    weaningCageLabels.addAll(labels);
                    tvResult.setText("");
                    if (pb != null) {
                        pb.setVisibility(android.view.View.GONE);
                    }
                    showPickWeaningTargetCageDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (pb != null) {
                        pb.setVisibility(android.view.View.GONE);
                    }
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", this::pickWeaningTargetCage);
                });
            }
        }).start();
    }

    private void showPickWeaningTargetCageDialog() {
        List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
        items.add(new PickerDialogUtil.PickItem(0, "自动（系统选择）", "自动"));
        for (int i = 0; i < weaningCageIds.size(); i++) {
            long id = weaningCageIds.get(i);
            String label = i < weaningCageLabels.size() ? weaningCageLabels.get(i) : String.valueOf(id);
            items.add(new PickerDialogUtil.PickItem(id, label, label));
        }
        List<Long> recent = new ArrayList<Long>();
        recent.add(0L);
        recent.addAll(recentStore.getIds("weaning_target_cage_" + session.getHouseId()));
        PickerDialogUtil.showSingle(this, "选择目标笼位", items, recent, it -> {
            if (it == null) {
                return;
            }
            if (it.id <= 0) {
                weaningTargetCageId = null;
                etWeaningTargetCage.setText("自动（系统选择）");
                Toast.makeText(this, "已设置为自动", Toast.LENGTH_SHORT).show();
                return;
            }
            weaningTargetCageId = it.id;
            etWeaningTargetCage.setText(it.label);
            recentStore.push("weaning_target_cage_" + session.getHouseId(), it.id, 20);
            Toast.makeText(this, "已选择目标笼位", Toast.LENGTH_SHORT).show();
        });
    }

    private void sale() {
        List<Long> ids = getCheckedIds(lvFattening, fatteningRabbitIds);
        if (ids.isEmpty()) {
            failOnList(lvFattening, "请在育肥商品兔列表勾选要出售的兔子");
            return;
        }
        JsonArray arr = new JsonArray();
        for (Long id : ids) {
            arr.add(id);
        }
        java.util.Date d = InputUtil.parseDate(etSaleDate.getText().toString().trim());
        if (d == null) {
            fail(etSaleDate, "日期格式错误：yyyy-MM-dd");
            return;
        }
        JsonObject body = new JsonObject();
        body.add("rabbitIds", arr);
        body.addProperty("saleDate", d.getTime());
        post("/api/batches/" + batchId + "/sale", body);
    }

    private void completeBatch() {
        java.util.Date d = InputUtil.parseDate(etBatchEndDate.getText().toString().trim());
        if (d == null) {
            d = new java.util.Date();
        }
        JsonObject body = new JsonObject();
        body.addProperty("endDate", d.getTime());
        body.addProperty("force", cbForceCompleteBatch.isChecked());
        body.addProperty("remark", "手动结束批次");
        post("/api/batches/" + batchId + "/complete", body);
    }

    private void post(String path, JsonObject body) {
        if (posting) {
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (!body.has("requestId")) {
            body.addProperty("requestId", java.util.UUID.randomUUID().toString());
        }
        String requestId = body.get("requestId").getAsString();
        posting = true;
        setAllActionEnabled(false);
        tvResult.setText("处理中...");
        new Thread(() -> {
            try {
                api.postJson(path, token, houseId, body);
                runOnUiThread(() -> {
                    String msg = "成功：" + titleOfPath(path) + "\n" + successSummary(path, body);
                    tvResult.setText(msg);
                    new AlertDialog.Builder(this)
                            .setTitle("操作成功")
                            .setMessage(msg)
                            .setPositiveButton("继续下一步", (d, which) -> {
                                updateNextHint();
                                if (lastSuggestedType != null && !lastSuggestedType.trim().isEmpty()) {
                                    fillDateForNextType(lastSuggestedType);
                                    fillMaleForNextType(lastSuggestedType);
                                    scrollToEventType(lastSuggestedType);
                                }
                            })
                            .setNegativeButton("关闭", null)
                            .show();
                    posting = false;
                    setAllActionEnabled(true);
                    loadBatchRabbits();
                    maybeAckFromEvent();
                });
            } catch (Exception e) {
                PendingOp op = new PendingOp();
                op.setId(String.valueOf(System.currentTimeMillis()) + "-" + java.util.UUID.randomUUID().toString());
                op.setTitle(titleOfPath(path));
                op.setPath(path);
                op.setHouseId(houseId);
                op.setCreateTime(System.currentTimeMillis());
                op.setBodyJson(body.toString());
                pendingStore.add(op);
                runOnUiThread(() -> {
                    String msg = String.valueOf(e.getMessage()) + "\nrequestId=" + requestId + "\n已存为待提交，可在“待提交”重试";
                    tvResult.setText(msg);
                    new AlertDialog.Builder(this)
                            .setTitle("操作失败")
                            .setMessage(msg)
                            .setPositiveButton("打开待提交", (d, which) -> startActivity(new Intent(this, PendingOpsActivity.class)))
                            .setNegativeButton("关闭", null)
                            .show();
                    posting = false;
                    setAllActionEnabled(true);
                    updateButtonState();
                });
            }
        }).start();
    }

    private String titleOfPath(String path) {
        if (path == null) {
            return "批次操作";
        }
        if (path.contains("/aphrodisiac/start")) {
            return "批次：催情开始";
        }
        if (path.contains("/aphrodisiac/finish")) {
            return "批次：催情完成";
        }
        if (path.contains("/mating")) {
            return "批次：配种";
        }
        if (path.contains("/pregnancy-check")) {
            return "批次：摸胎";
        }
        if (path.contains("/prepartum/finish")) {
            return "批次：备产完成";
        }
        if (path.contains("/parturition")) {
            return "批次：分娩";
        }
        if (path.contains("/weaning")) {
            return "批次：断奶";
        }
        if (path.contains("/sale")) {
            return "批次：出售";
        }
        if (path.contains("/complete")) {
            return "批次：结束";
        }
        return "批次操作";
    }

    private void setAllActionEnabled(boolean enabled) {
        btnLoadBatchRabbits.setEnabled(enabled);
        btnAphStart.setEnabled(enabled);
        btnAphFinish.setEnabled(enabled);
        btnPickMale.setEnabled(enabled);
        btnMating.setEnabled(enabled);
        btnPregCheck.setEnabled(enabled);
        btnPrepartumFinish.setEnabled(enabled);
        btnParturition.setEnabled(enabled);
        btnWeaning.setEnabled(enabled);
        btnSale.setEnabled(enabled);
        btnCompleteBatch.setEnabled(enabled);
    }

    private void updateButtonState() {
        String status = getSelectedBreedingStatus();
        String nextType = getSelectedBreedingNextType();
        boolean hasAnyBreedingChecked = getCheckedIds(lvBreeding, breedingRabbitIds).size() > 0;
        boolean hasSingleBreedingChecked = getSingleCheckedId(lvBreeding, breedingRabbitIds) != null;

        if (posting) {
            setAllActionEnabled(false);
            return;
        }

        btnAphStart.setEnabled(hasAnyBreedingChecked && allCheckedBreedingStatusIn("待催情", "休整期"));
        btnAphFinish.setEnabled(hasAnyBreedingChecked && allCheckedBreedingStatusIn("催情中"));
        btnMating.setEnabled(hasSingleBreedingChecked && "待配种".equals(status));
        btnPregCheck.setEnabled(hasSingleBreedingChecked && "已配种".equals(status));
        btnPrepartumFinish.setEnabled(hasSingleBreedingChecked && "怀孕确认".equals(status) && "备产".equals(nextType));
        btnParturition.setEnabled(hasSingleBreedingChecked && "怀孕确认".equals(status) && (nextType == null || nextType.isEmpty() || "分娩".equals(nextType)));
        btnWeaning.setEnabled(hasSingleBreedingChecked && "哺乳中".equals(status));
        btnSale.setEnabled(getCheckedIds(lvFattening, fatteningRabbitIds).size() > 0);
        btnCompleteBatch.setEnabled(true);
    }

    private void updateNextHint() {
        if (tvNextHint == null || tvSelected == null) {
            return;
        }
        lastSuggestedType = null;
        List<Long> breedingChecked = getCheckedIds(lvBreeding, breedingRabbitIds);
        List<Long> fatteningChecked = getCheckedIds(lvFattening, fatteningRabbitIds);

        if (breedingChecked.isEmpty() && fatteningChecked.isEmpty()) {
            tvNextHint.setText("下一步：请选择母兔或育肥兔");
            tvSelected.setText("");
            return;
        }

        if (!breedingChecked.isEmpty()) {
            if (breedingChecked.size() > 1) {
                tvSelected.setText("已选母兔：" + breedingChecked.size() + " 只（用于批量催情）");
                tvNextHint.setText("下一步：开始催情（可多选）");
                lastSuggestedType = "催情";
                return;
            }
            int idx = firstCheckedIndex(lvBreeding);
            if (idx < 0 || idx >= breedingRabbitIds.size()) {
                tvNextHint.setText("下一步：请选择母兔");
                tvSelected.setText("");
                return;
            }
            long rabbitId = breedingRabbitIds.get(idx);
            String status = idx < breedingStatuses.size() ? breedingStatuses.get(idx) : "";
            String nextType = idx < breedingNextTypes.size() ? breedingNextTypes.get(idx) : "";
            Long nextMs = idx < breedingNextDatesMs.size() ? breedingNextDatesMs.get(idx) : null;
            String nextDate = nextMs == null ? "" : TimeUtil.fmt(nextMs);
            tvSelected.setText("母兔#" + rabbitId + "  状态:" + safe(status) + "\n下一步:" + safe(nextType) + "  " + safe(nextDate));

            if ("待催情".equals(status) || "休整期".equals(status)) {
                tvNextHint.setText("下一步：开始催情（可多选）→ 完成催情 → 配种");
                lastSuggestedType = "催情";
                return;
            }
            if ("催情中".equals(status)) {
                tvNextHint.setText("下一步：完成催情 → 配种");
                lastSuggestedType = "催情";
                return;
            }
            if ("待配种".equals(status)) {
                tvNextHint.setText("下一步：配种（先选公兔+日期）");
                lastSuggestedType = "配种";
                return;
            }
            if ("已配种".equals(status)) {
                tvNextHint.setText("下一步：摸胎（选择结果并提交）");
                lastSuggestedType = "摸胎";
                return;
            }
            if ("不确定".equals(status)) {
                tvNextHint.setText("下一步：再次摸胎（选择结果并提交）");
                lastSuggestedType = "摸胎";
                return;
            }
            if ("怀孕确认".equals(status)) {
                if ("备产".equals(nextType)) {
                    tvNextHint.setText("下一步：备产完成（放产箱）");
                    lastSuggestedType = "备产";
                    return;
                }
                tvNextHint.setText("下一步：分娩登记");
                lastSuggestedType = "分娩";
                return;
            }
            if ("哺乳中".equals(status)) {
                tvNextHint.setText("下一步：断奶登记");
                lastSuggestedType = "断奶";
                return;
            }
            tvNextHint.setText("下一步：按当前状态继续流程");
            return;
        }

        if (fatteningChecked.size() > 0) {
            tvSelected.setText("已选育肥兔：" + fatteningChecked.size() + " 只（用于出售）");
            tvNextHint.setText("下一步：出售（填写日期后提交）");
            lastSuggestedType = "出售";
        }
    }

    private int firstCheckedIndex(ListView lv) {
        SparseBooleanArray checked = lv.getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            int idx = checked.keyAt(i);
            if (checked.valueAt(i)) {
                return idx;
            }
        }
        return -1;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String getSelectedBreedingStatus() {
        SparseBooleanArray checked = lvBreeding.getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            int idx = checked.keyAt(i);
            if (checked.valueAt(i) && idx >= 0 && idx < breedingStatuses.size()) {
                return breedingStatuses.get(idx);
            }
        }
        return null;
    }

    private String getSelectedBreedingNextType() {
        SparseBooleanArray checked = lvBreeding.getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            int idx = checked.keyAt(i);
            if (checked.valueAt(i) && idx >= 0 && idx < breedingNextTypes.size()) {
                return breedingNextTypes.get(idx);
            }
        }
        return null;
    }

    private void autoSelectFromExtras() {
        if (preselectRabbitId <= 0) {
            return;
        }
        for (int i = 0; i < breedingRabbitIds.size(); i++) {
            if (breedingRabbitIds.get(i) != null && breedingRabbitIds.get(i) == preselectRabbitId) {
                lvBreeding.setItemChecked(i, true);
                String nextType = i < breedingNextTypes.size() ? breedingNextTypes.get(i) : null;
                String t = preselectEventType != null && !preselectEventType.isEmpty() ? preselectEventType : nextType;
                fillDateForNextType(t);
                fillMaleForNextType(t);
                scrollToEventType(t);
                preselectRabbitId = 0L;
                return;
            }
        }
        for (int i = 0; i < fatteningRabbitIds.size(); i++) {
            if (fatteningRabbitIds.get(i) != null && fatteningRabbitIds.get(i) == preselectRabbitId) {
                lvFattening.setItemChecked(i, true);
                String nextType = i < fatteningNextTypes.size() ? fatteningNextTypes.get(i) : null;
                String t = preselectEventType != null && !preselectEventType.isEmpty() ? preselectEventType : nextType;
                fillDateForNextType(t);
                scrollToEventType(t);
                preselectRabbitId = 0L;
                return;
            }
        }
    }

    private void fillDateForNextType(String nextType) {
        if (nextType == null) {
            return;
        }
        String t = nextType.trim();
        if (t.isEmpty()) {
            return;
        }
        String today = TimeUtil.today();
        if ("配种".equals(t) || "mating".equalsIgnoreCase(t)) {
            etMatingDate.setText(today);
            return;
        }
        if ("摸胎".equals(t) || "pregnancy_check".equalsIgnoreCase(t)) {
            etCheckDate.setText(today);
            return;
        }
        if ("备产".equals(t) || "prepartum".equalsIgnoreCase(t)) {
            etPrepartumDate.setText(today);
            return;
        }
        if ("分娩".equals(t) || "parturition".equalsIgnoreCase(t)) {
            etBirthDate.setText(today);
            return;
        }
        if ("断奶".equals(t) || "weaning".equalsIgnoreCase(t)) {
            etWeaningDate.setText(today);
            return;
        }
        if ("出售".equals(t) || "sale".equalsIgnoreCase(t)) {
            etSaleDate.setText(today);
        }
    }

    private void scrollToEventType(String nextType) {
        if (sv == null || nextType == null) {
            return;
        }
        String t = nextType.trim();
        if (t.isEmpty()) {
            return;
        }
        android.view.View anchor = null;
        if ("催情".equals(t) || "aphrodisiac".equalsIgnoreCase(t)) {
            anchor = lvBreeding;
        } else if ("配种".equals(t) || "mating".equalsIgnoreCase(t)) {
            anchor = etMatingDate;
        } else if ("摸胎".equals(t) || "pregnancy_check".equalsIgnoreCase(t)) {
            anchor = etCheckDate;
        } else if ("备产".equals(t) || "prepartum".equalsIgnoreCase(t)) {
            anchor = etPrepartumDate;
        } else if ("分娩".equals(t) || "parturition".equalsIgnoreCase(t)) {
            anchor = etBirthDate;
        } else if ("断奶".equals(t) || "weaning".equalsIgnoreCase(t)) {
            anchor = etWeaningDate;
        } else if ("出售".equals(t) || "sale".equalsIgnoreCase(t)) {
            anchor = etSaleDate;
        }
        if (anchor == null) {
            return;
        }
        android.view.View finalAnchor = anchor;
        sv.post(() -> {
            finalAnchor.requestFocus();
            sv.smoothScrollTo(0, finalAnchor.getTop());
        });
    }

    private boolean allCheckedBreedingStatusIn(String... allowed) {
        SparseBooleanArray checked = lvBreeding.getCheckedItemPositions();
        boolean has = false;
        for (int i = 0; i < checked.size(); i++) {
            int idx = checked.keyAt(i);
            if (!checked.valueAt(i)) {
                continue;
            }
            if (idx < 0 || idx >= breedingStatuses.size()) {
                return false;
            }
            has = true;
            String s = breedingStatuses.get(idx);
            boolean ok = false;
            for (String a : allowed) {
                if (a != null && a.equals(s)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return has;
    }

    private List<Long> getCheckedIds(ListView lv, List<Long> ids) {
        SparseBooleanArray checked = lv.getCheckedItemPositions();
        List<Long> res = new ArrayList<Long>();
        for (int i = 0; i < checked.size(); i++) {
            int idx = checked.keyAt(i);
            if (checked.valueAt(i) && idx >= 0 && idx < ids.size()) {
                res.add(ids.get(idx));
            }
        }
        return res;
    }

    private Long getSingleCheckedId(ListView lv, List<Long> ids) {
        SparseBooleanArray checked = lv.getCheckedItemPositions();
        Long r = null;
        for (int i = 0; i < checked.size(); i++) {
            int idx = checked.keyAt(i);
            if (checked.valueAt(i) && idx >= 0 && idx < ids.size()) {
                if (r != null) {
                    return null;
                }
                r = ids.get(idx);
            }
        }
        return r;
    }

    private void pickMale() {
        if (!maleRabbitIds.isEmpty()) {
            showPickMaleDialog();
            return;
        }
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再选择公兔", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再选择公兔", "关闭", this::finish);
            return;
        }
        tvResult.setText("加载公兔列表中...");
        if (pb != null) {
            pb.setVisibility(android.view.View.VISIBLE);
        }
        state.hide();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/rabbits?type=0&active=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<Long> ids = new ArrayList<Long>();
                List<String> labels = new ArrayList<String>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    String gender = o.has("gender") && !o.get("gender").isJsonNull() ? o.get("gender").getAsString() : "";
                    if (!"1".equals(gender)) {
                        continue;
                    }
                    long id = o.get("id").getAsLong();
                    String cageId = o.has("cageId") && !o.get("cageId").isJsonNull() ? o.get("cageId").getAsString() : "";
                    ids.add(id);
                    labels.add("id=" + id + " cageId=" + cageId);
                }
                runOnUiThread(() -> {
                    maleRabbitIds.clear();
                    maleRabbitLabels.clear();
                    maleRabbitIds.addAll(ids);
                    maleRabbitLabels.addAll(labels);
                    if (pb != null) {
                        pb.setVisibility(android.view.View.GONE);
                    }
                    if (maleRabbitIds.isEmpty()) {
                        tvResult.setText("");
                        state.showEmpty("🐇", "没有可用种公兔", "未找到 type=0, gender=1, active=true 的公兔", "刷新", this::pickMale);
                        return;
                    }
                    tvResult.setText("");
                    state.hide();
                    showPickMaleDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (pb != null) {
                        pb.setVisibility(android.view.View.GONE);
                    }
                    tvResult.setText("");
                    state.showError(e.getMessage(), "重试", this::pickMale);
                });
            }
        }).start();
    }

    private void showPickMaleDialog() {
        List<PickerDialogUtil.PickItem> items = new ArrayList<PickerDialogUtil.PickItem>();
        for (int i = 0; i < maleRabbitIds.size(); i++) {
            long id = maleRabbitIds.get(i);
            String label = i < maleRabbitLabels.size() ? maleRabbitLabels.get(i) : String.valueOf(id);
            items.add(new PickerDialogUtil.PickItem(id, label, label));
        }
        List<Long> recent = recentStore.getIds("male_rabbit_" + session.getHouseId());
        PickerDialogUtil.showSingle(this, "选择公兔", items, recent, it -> {
            if (it == null) {
                return;
            }
            etMaleId.setText(String.valueOf(it.id));
            recentStore.push("male_rabbit_" + session.getHouseId(), it.id, 10);
            Toast.makeText(this, "已选择公兔", Toast.LENGTH_SHORT).show();
        });
    }

    private void fillMaleForNextType(String nextType) {
        if (nextType == null) {
            return;
        }
        String t = nextType.trim();
        if (!"配种".equals(t) && !"mating".equalsIgnoreCase(t)) {
            return;
        }
        if (!etMaleId.getText().toString().trim().isEmpty()) {
            return;
        }
        List<Long> recent = recentStore.getIds("male_rabbit_" + session.getHouseId());
        if (!recent.isEmpty()) {
            etMaleId.setText(String.valueOf(recent.get(0)));
        }
    }

    private String formatDueText(Long nextMs, long now) {
        if (nextMs == null || nextMs <= 0) {
            return "";
        }
        long dayMs = 24L * 60L * 60L * 1000L;
        long diff = nextMs - now;
        if (diff <= 0) {
            long over = (-diff + dayMs - 1) / dayMs;
            if (over <= 0) {
                over = 0;
            }
            return "【已超期" + over + "天】";
        }
        long left = (diff + dayMs - 1) / dayMs;
        return "【到期" + left + "天】";
    }

    private void fail(EditText et, String msg) {
        tvResult.setText(msg);
        if (et != null) {
            et.setError(msg);
            et.requestFocus();
            sv.post(() -> sv.smoothScrollTo(0, et.getTop()));
        }
    }

    private void failOnList(ListView lv, String msg) {
        tvResult.setText(msg);
        if (lv != null) {
            lv.requestFocus();
            sv.post(() -> sv.smoothScrollTo(0, lv.getTop()));
        }
    }

    private String successSummary(String path, JsonObject body) {
        if (path == null || body == null) {
            return "";
        }
        if (path.contains("/mating")) {
            return "母兔#" + body.get("femaleRabbitId").getAsLong() + "  公兔#" + body.get("maleRabbitId").getAsLong() + "  日期:" + TimeUtil.fmtAny(body.get("matingDate").getAsString());
        }
        if (path.contains("/pregnancy-check")) {
            return "母兔#" + body.get("rabbitId").getAsLong() + "  结果:" + safe(body.get("result").getAsString()) + "  日期:" + TimeUtil.fmtAny(body.get("checkDate").getAsString());
        }
        if (path.contains("/prepartum/finish")) {
            return "母兔#" + body.get("rabbitId").getAsLong() + "  日期:" + TimeUtil.fmtAny(body.get("actionDate").getAsString());
        }
        if (path.contains("/parturition")) {
            return "母兔#" + body.get("rabbitId").getAsLong() + "  日期:" + TimeUtil.fmtAny(body.get("birthDate").getAsString()) + "  total:" + body.get("totalKits").getAsString() + "  live:" + body.get("liveKits").getAsString();
        }
        if (path.contains("/weaning")) {
            String cage = body.has("targetCageId") && !body.get("targetCageId").isJsonNull() ? ("  targetCage#" + body.get("targetCageId").getAsString()) : "";
            return "母兔#" + body.get("rabbitId").getAsLong() + "  日期:" + TimeUtil.fmtAny(body.get("weaningDate").getAsString()) + "  count:" + body.get("weaningCount").getAsString() + cage;
        }
        if (path.contains("/sale")) {
            return "数量:" + (body.get("rabbitIds").isJsonArray() ? body.getAsJsonArray("rabbitIds").size() : 0) + "  日期:" + TimeUtil.fmtAny(body.get("saleDate").getAsString());
        }
        if (path.contains("/aphrodisiac/start")) {
            return "数量:" + (body.get("rabbitIds").isJsonArray() ? body.getAsJsonArray("rabbitIds").size() : 0);
        }
        if (path.contains("/aphrodisiac/finish")) {
            return "数量:" + (body.get("rabbitIds").isJsonArray() ? body.getAsJsonArray("rabbitIds").size() : 0);
        }
        if (path.contains("/complete")) {
            return "endDate:" + TimeUtil.fmtAny(body.get("endDate").getAsString()) + (body.has("force") && body.get("force").getAsBoolean() ? "  force=true" : "");
        }
        return "";
    }

    private void maybeAckFromEvent() {
        if (fromEventRecordId <= 0 || fromEventCategory == null || fromEventCategory.trim().isEmpty()) {
            return;
        }
        String category = fromEventCategory;
        long recordId = fromEventRecordId;
        fromEventCategory = null;
        fromEventRecordId = 0L;
        String token = session.getToken();
        long houseId = session.getHouseId();
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("category", category);
                body.addProperty("recordId", recordId);
                body.addProperty("action", "ack");
                api.postJson("/api/events/ack", token, houseId, body);
                runOnUiThread(() -> Toast.makeText(this, "提醒已自动确认", Toast.LENGTH_SHORT).show());
            } catch (Exception ignored) {
            }
        }).start();
    }

    private Long parseMillis(JsonObject o, String key) {
        if (o == null || key == null || key.isEmpty() || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        try {
            return o.get(key).getAsLong();
        } catch (Exception ignored) {
        }
        try {
            return Long.parseLong(o.get(key).getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Integer parseNullableInt(String s) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(t);
        } catch (Exception ignored) {
            return null;
        }
    }
}
