package com.rabbit.app.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.PendingOp;
import com.rabbit.app.storage.PendingOpStore;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class PendingOpsActivity extends AppCompatActivity {
    private TextView tvInfo;
    private Button btnRetryAll;
    private Button btnRefresh;
    private ListView lv;
    private ProgressBar pb;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private PendingOpStore store;
    private SessionStore session;
    private ApiClient api;
    private Gson gson;
    private StatePanel state;

    private ArrayAdapter<String> adapter;
    private final List<PendingOp> ops = new ArrayList<PendingOp>();
    private boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_ops);

        tvInfo = findViewById(R.id.tvPendingInfo);
        btnRetryAll = findViewById(R.id.btnRetryAll);
        btnRefresh = findViewById(R.id.btnRefreshPending);
        lv = findViewById(R.id.lvPending);
        pb = findViewById(R.id.pbPendingLoading);

        store = new PendingOpStore(this);
        session = new SessionStore(this);
        api = new ApiClient();
        gson = new Gson();
        state = new StatePanel(this);

        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("待提交");
        tvTopHouse.setText("用户：" + safe(session.getUserName()) + "  ·  兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.VISIBLE);
        btnTopRight.setText("刷新");
        btnTopRight.setOnClickListener(v -> load());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        btnRefresh.setOnClickListener(v -> load());
        btnRetryAll.setOnClickListener(v -> retryAll());

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= ops.size()) {
                return;
            }
            PendingOp op = ops.get(position);
            new AlertDialog.Builder(this)
                    .setTitle(op.getTitle())
                    .setItems(new String[]{"重试", "删除"}, (d, which) -> {
                        if (which == 0) {
                            retryOne(op);
                        } else {
                            store.remove(op.getId());
                            load();
                        }
                    })
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ops.clear();
        ops.addAll(store.list());
        List<String> items = new ArrayList<String>();
        for (PendingOp op : ops) {
            String line1 = safe(op.getTitle()) + "  " + TimeUtil.fmtMs(op.getCreateTime());
            String line2 = "兔舍#" + op.getHouseId() + "  接口:" + safe(op.getPath());
            items.add(line1 + "\n" + line2);
        }
        adapter.clear();
        adapter.addAll(items);
        adapter.notifyDataSetChanged();
        tvInfo.setText(items.isEmpty() ? "暂无待提交" : "点击某条可重试/删除");
        if (items.isEmpty()) {
            state.showEmpty("📦", "暂无待提交", "网络恢复后会自动重试失败操作；也可在这里手动重试", "刷新", this::load);
        } else {
            state.hide();
        }
    }

    private void retryAll() {
        if (running) {
            return;
        }
        if (ops.isEmpty()) {
            Toast.makeText(this, "暂无待提交", Toast.LENGTH_SHORT).show();
            return;
        }
        running = true;
        pb.setVisibility(android.view.View.VISIBLE);
        new Thread(() -> {
            int ok = 0;
            int fail = 0;
            List<PendingOp> remain = new ArrayList<PendingOp>();
            for (PendingOp op : store.list()) {
                try {
                    postOp(op);
                    ok++;
                } catch (Exception e) {
                    remain.add(op);
                    fail++;
                }
            }
            store.replaceAll(remain);
            int finalOk = ok;
            int finalFail = fail;
            runOnUiThread(() -> {
                pb.setVisibility(android.view.View.GONE);
                running = false;
                load();
                Toast.makeText(this, "重试完成：成功" + finalOk + " 失败" + finalFail, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void retryOne(PendingOp op) {
        if (running) {
            return;
        }
        running = true;
        pb.setVisibility(android.view.View.VISIBLE);
        new Thread(() -> {
            try {
                postOp(op);
                store.remove(op.getId());
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    running = false;
                    load();
                    Toast.makeText(this, "重试成功", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    running = false;
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void postOp(PendingOp op) throws Exception {
        String token = session.getToken();
        long houseId = op.getHouseId();
        JsonObject body = gson.fromJson(op.getBodyJson(), JsonObject.class);
        String method = op.getMethod() == null ? "" : op.getMethod().trim().toUpperCase();
        if ("PUT".equals(method)) {
            api.putJson(op.getPath(), token, houseId, body);
            return;
        }
        api.postJson(op.getPath(), token, houseId, body);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
