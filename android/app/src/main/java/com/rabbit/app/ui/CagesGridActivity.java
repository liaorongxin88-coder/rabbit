package com.rabbit.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CagesGridActivity extends AppCompatActivity {
    private TextView tvTitle;
    private EditText etSearch;
    private Button btnClear;
    private Button btnRefresh;
    private Button btnManage;
    private Spinner spLayer;
    private ProgressBar pb;
    private GridLayout grid;
    private TextView tvLegend;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;

    private ApiClient api;
    private SessionStore session;
    private StatePanel state;

    private final Map<String, CageItem> cageMap = new HashMap<String, CageItem>();
    private int maxR = 0;
    private int maxC = 0;
    private int maxL = 0;

    private ArrayAdapter<String> layerAdapter;
    private final List<Integer> layers = new ArrayList<Integer>();
    private String currentQuery = "";

    private static class CageItem {
        long id;
        String cageNumber;
        String status;
        int rabbitCount;
        boolean isFed;
        boolean isEnabled;
        int r;
        int c;
        int l;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cages_grid);

        api = new ApiClient();
        session = new SessionStore(this);
        state = new StatePanel(this);

        tvTitle = findViewById(R.id.tvCageGridTitle);
        etSearch = findViewById(R.id.etCageSearch);
        btnClear = findViewById(R.id.btnCageClear);
        btnRefresh = findViewById(R.id.btnCageRefresh);
        btnManage = findViewById(R.id.btnCageManage);
        spLayer = findViewById(R.id.spLayer);
        pb = findViewById(R.id.pbCageGridLoading);
        grid = findViewById(R.id.gridCages);
        tvLegend = findViewById(R.id.tvCageLegend);
        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);

        tvTopTitle.setText("笼位网格");
        btnTopBack.setOnClickListener(v -> finish());
        refreshHeader();
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());
        tvTitle.setText("提示：点格子进笼位；长按可绑定NFC");

        layerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        layerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLayer.setAdapter(layerAdapter);

        btnClear.setOnClickListener(v -> etSearch.setText(""));
        btnRefresh.setOnClickListener(v -> load());
        btnManage.setOnClickListener(v -> startActivity(new Intent(this, CagesManageActivity.class)));
        tvLegend.setText("空=绿  有兔=蓝  已喂=更深  停用=灰");

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString().trim();
                buildGrid(getSelectedLayer());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        spLayer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                buildGrid(getSelectedLayer());
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void refreshHeader() {
        long houseId = session.getHouseId();
        tvTopHouse.setText("用户：" + safe(session.getUserName()) + "  兔舍ID：" + (houseId <= 0 ? "未选择" : String.valueOf(houseId)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHeader();
        load();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void load() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (houseId <= 0) {
            state.showEmpty("🏠", "请先选择兔舍", "选择兔舍后再加载笼位网格", "刷新", this::load);
            return;
        }
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/cages", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                parseCages(arr);
                runOnUiThread(() -> {
                    updateLayers();
                    pb.setVisibility(android.view.View.GONE);
                    buildGrid(getSelectedLayer());
                    if (cageMap.isEmpty()) {
                        state.showEmpty("🧱", "暂无笼位", "请先在“笼位维护”初始化或检查是否已创建兔舍布局", "刷新", this::load);
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

    private void parseCages(JsonArray arr) {
        cageMap.clear();
        maxR = 0;
        maxC = 0;
        maxL = 0;
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            CageItem item = new CageItem();
            item.id = o.get("id").getAsLong();
            item.cageNumber = o.get("cageNumber").getAsString();
            item.status = o.get("status").getAsString();
            item.rabbitCount = o.get("rabbitCount").getAsInt();
            item.isFed = o.get("isFed").getAsBoolean();
            item.isEnabled = !o.has("isEnabled") || o.get("isEnabled").isJsonNull() || o.get("isEnabled").getAsBoolean();
            int[] rcL = parseNumber(item.cageNumber);
            item.r = rcL[0];
            item.c = rcL[1];
            item.l = rcL[2];
            if (item.r > maxR) maxR = item.r;
            if (item.c > maxC) maxC = item.c;
            if (item.l > maxL) maxL = item.l;
            cageMap.put(key(item.r, item.c, item.l), item);
        }
    }

    private void updateLayers() {
        layers.clear();
        List<String> labels = new ArrayList<String>();
        int l = maxL <= 0 ? 1 : maxL;
        for (int i = 1; i <= l; i++) {
            layers.add(i);
            labels.add(String.valueOf(i));
        }
        layerAdapter.clear();
        layerAdapter.addAll(labels);
        layerAdapter.notifyDataSetChanged();
        if (!layers.isEmpty() && spLayer.getSelectedItemPosition() < 0) {
            spLayer.setSelection(0);
        }
    }

    private int getSelectedLayer() {
        int p = spLayer.getSelectedItemPosition();
        if (p < 0 || p >= layers.size()) {
            return 1;
        }
        return layers.get(p);
    }

    private void buildGrid(int layer) {
        grid.removeAllViews();
        int rows = maxR <= 0 ? 1 : maxR;
        int cols = maxC <= 0 ? 1 : maxC;
        grid.setRowCount(rows);
        grid.setColumnCount(cols);

        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                CageItem item = cageMap.get(key(r, c, layer));
                TextView tv = new TextView(this);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(10, 10, 10, 10);
                tv.setMinWidth(140);
                tv.setMinHeight(120);
                tv.setTextSize(12f);
                String label = r + "-" + c + "\n" + (item == null ? "-" : String.valueOf(item.rabbitCount));
                if (item != null && item.isFed) {
                    label = label + " 喂";
                }
                if (item != null && !item.isEnabled) {
                    label = label + " 停";
                }
                tv.setText(label);
                tv.setTextColor(Color.BLACK);

                if (item == null) {
                    tv.setBackgroundColor(Color.parseColor("#EEEEEE"));
                } else {
                    boolean match = currentQuery != null && !currentQuery.isEmpty() && item.cageNumber.contains(currentQuery);
                    if (match) {
                        tv.setBackgroundColor(Color.parseColor("#FFF9C4"));
                        tv.setTextColor(Color.parseColor("#D32F2F"));
                    } else {
                        tv.setBackgroundColor(colorOf(item));
                    }
                    if (item.isEnabled) {
                        tv.setOnClickListener(v -> openCage(item));
                        tv.setOnLongClickListener(v -> {
                            openNfcBind(item);
                            return true;
                        });
                    }
                }

                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                tv.setLayoutParams(lp);
                grid.addView(tv);
            }
        }
    }

    private int colorOf(CageItem item) {
        if (!item.isEnabled) {
            return Color.parseColor("#E0E0E0");
        }
        if (item.rabbitCount <= 0) {
            return Color.parseColor("#C8E6C9");
        }
        if (item.isFed) {
            return Color.parseColor("#90CAF9");
        }
        return Color.parseColor("#BBDEFB");
    }

    private void openCage(CageItem item) {
        Intent it = new Intent(this, RabbitsActivity.class);
        it.putExtra("cageId", item.id);
        it.putExtra("cageNumber", item.cageNumber);
        startActivity(it);
    }

    private void openNfcBind(CageItem item) {
        Intent it = new Intent(this, NfcBindActivity.class);
        it.putExtra("cageId", item.id);
        it.putExtra("cageNumber", item.cageNumber);
        startActivity(it);
    }

    private int[] parseNumber(String cageNumber) {
        try {
            String[] parts = cageNumber.split("-");
            if (parts.length >= 3) {
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
            }
        } catch (Exception ignored) {
        }
        return new int[]{0, 0, 0};
    }

    private String key(int r, int c, int l) {
        return r + "-" + c + "-" + l;
    }
}
