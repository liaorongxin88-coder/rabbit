package com.rabbit.app.ui;

import android.os.Bundle;
import android.content.Intent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.R;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class EventsActivity extends AppCompatActivity {
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
    private final List<Long> recordIds = new ArrayList<Long>();
    private final List<String> categories = new ArrayList<String>();
    private final List<Long> batchIds = new ArrayList<Long>();
    private final List<Long> rabbitIds = new ArrayList<Long>();
    private final List<String> eventTypes = new ArrayList<String>();
    private boolean acting;

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

        tvTopTitle.setText("提醒");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(android.view.View.VISIBLE);
        btnTopRight.setText("刷新");
        btnTopRight.setOnClickListener(v -> load());
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        lv.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= recordIds.size()) {
                return true;
            }
            if (!"后备成熟".equals(categories.get(position))) {
                return true;
            }
            markNotified(recordIds.get(position));
            return true;
        });

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= categories.size()) {
                return;
            }
            String category = categories.get(position);
            long recordId = position < recordIds.size() ? recordIds.get(position) : 0L;
            String eventType = position < eventTypes.size() ? eventTypes.get(position) : null;
            if (recordId <= 0) {
                Toast.makeText(this, "recordId缺失", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] actions;
            if ("生产".equals(category)) {
                actions = new String[]{"进入处理", "稍后提醒(2小时)", "忽略", "确认"};
            } else if ("后备成熟".equals(category)) {
                actions = new String[]{"标记已提醒", "稍后提醒(2小时)", "忽略", "确认"};
            } else if ("治疗复查".equals(category)) {
                actions = new String[]{"进入处理", "稍后提醒(2小时)", "忽略", "确认"};
            } else {
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("操作")
                    .setItems(actions, (d, which) -> {
                        if ("生产".equals(category)) {
                            if (which == 0) {
                                long batchId = position < batchIds.size() ? batchIds.get(position) : 0L;
                                long rabbitId = position < rabbitIds.size() ? rabbitIds.get(position) : 0L;
                                if (batchId <= 0 || rabbitId <= 0) {
                                    Toast.makeText(this, "该提醒缺少 batchId/rabbitId", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                Intent it = new Intent(this, BatchOpsActivity.class);
                                it.putExtra("batchId", batchId);
                                it.putExtra("rabbitId", rabbitId);
                                if (eventType != null) {
                                    it.putExtra("eventType", eventType);
                                }
                                it.putExtra("eventCategory", category);
                                it.putExtra("eventRecordId", recordId);
                                startActivity(it);
                                return;
                            }
                            if (which == 1) {
                                snooze(category, recordId, 2);
                                return;
                            }
                            if (which == 2) {
                                ack(category, recordId, "ignore");
                                return;
                            }
                            if (which == 3) {
                                ack(category, recordId, "ack");
                            }
                        } else if ("后备成熟".equals(category)) {
                            if (which == 0) {
                                markNotified(recordId);
                                return;
                            }
                            if (which == 1) {
                                snooze(category, recordId, 2);
                                return;
                            }
                            if (which == 2) {
                                ack(category, recordId, "ignore");
                                return;
                            }
                            if (which == 3) {
                                ack(category, recordId, "ack");
                            }
                        } else if ("治疗复查".equals(category)) {
                            if (which == 0) {
                                long rabbitId = position < rabbitIds.size() ? rabbitIds.get(position) : 0L;
                                if (rabbitId <= 0) {
                                    Toast.makeText(this, "该提醒缺少 rabbitId", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                Intent it = new Intent(this, TreatmentToolActivity.class);
                                it.putExtra("rabbitId", rabbitId);
                                it.putExtra("treatmentId", recordId);
                                startActivity(it);
                                return;
                            }
                            if (which == 1) {
                                snooze(category, recordId, 2);
                                return;
                            }
                            if (which == 2) {
                                ack(category, recordId, "ignore");
                                return;
                            }
                            if (which == 3) {
                                ack(category, recordId, "ack");
                            }
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
        String token = session.getToken();
        long houseId = session.getHouseId();
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/events?onlyUnnotified=true", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                List<String> items = new ArrayList<String>();
                recordIds.clear();
                categories.clear();
                batchIds.clear();
                rabbitIds.clear();
                eventTypes.clear();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long recordId = o.has("recordId") && !o.get("recordId").isJsonNull() ? o.get("recordId").getAsLong() : 0L;
                    String category = o.has("category") ? o.get("category").getAsString() : "";
                    String eventType = o.has("eventType") ? o.get("eventType").getAsString() : "";
                    String eventDate = o.has("eventDate") && !o.get("eventDate").isJsonNull() ? TimeUtil.fmtAny(o.get("eventDate").getAsString()) : "";
                    long batchId = o.has("batchId") && !o.get("batchId").isJsonNull() ? o.get("batchId").getAsLong() : 0L;
                    long rabbitId = o.has("rabbitId") && !o.get("rabbitId").isJsonNull() ? o.get("rabbitId").getAsLong() : 0L;
                    String status = o.has("status") && !o.get("status").isJsonNull() ? o.get("status").getAsString() : "";
                    recordIds.add(recordId);
                    categories.add(category);
                    batchIds.add(batchId);
                    rabbitIds.add(rabbitId);
                    eventTypes.add(eventType);
                    if ("生产".equals(category)) {
                        String line1 = category + " · " + safe(eventType) + " · " + safe(eventDate);
                        String st = safe(status);
                        if ("overdue".equalsIgnoreCase(st)) {
                            st = "逾期";
                        } else if ("due".equalsIgnoreCase(st)) {
                            st = "到期";
                        } else if ("upcoming".equalsIgnoreCase(st)) {
                            st = "未到期";
                        }
                        String line2 = "批次#" + batchId + "  兔#" + rabbitId + "  状态:" + safe(status);
                        items.add(line1 + "||status:" + st + "\n" + line2);
                    } else if ("后备成熟".equals(category)) {
                        String line1 = category + " · " + safe(eventType) + " · " + safe(eventDate);
                        String line2 = "兔#" + rabbitId + "（点开可标记/稍后/忽略/确认）";
                        items.add(line1 + "||status:待处理\n" + line2);
                    } else {
                        items.add(category + " " + eventType + " " + eventDate);
                    }
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    if (items.isEmpty()) {
                        state.showEmpty("⏰", "暂无提醒", "可以先去批次/投喂补齐记录", "刷新", this::load);
                    } else {
                        state.hide();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    pb.setVisibility(android.view.View.GONE);
                    state.showError(e.getMessage(), "重试", this::load);
                });
            }
        }).start();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void markNotified(long recordId) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            state.showEmpty("🔒", "未登录", "请先登录后再操作", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再操作", "关闭", this::finish);
            return;
        }
        if (acting) {
            return;
        }
        acting = true;
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                JsonArray ids = new JsonArray();
                ids.add(recordId);
                body.add("recordIds", ids);
                body.addProperty("requestId", java.util.UUID.randomUUID().toString());
                api.postJson("/api/replacement-records/mark-notified", token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    Toast.makeText(this, "已标记", Toast.LENGTH_SHORT).show();
                    load();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    state.showError(e.getMessage(), "重试", () -> markNotified(recordId));
                });
            }
        }).start();
    }

    private void ack(String category, long recordId, String action) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            state.showEmpty("🔒", "未登录", "请先登录后再操作", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再操作", "关闭", this::finish);
            return;
        }
        if (acting) {
            return;
        }
        acting = true;
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("category", category);
                body.addProperty("recordId", recordId);
                body.addProperty("action", action);
                api.postJson("/api/events/ack", token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    Toast.makeText(this, "已处理", Toast.LENGTH_SHORT).show();
                    load();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    state.showError(e.getMessage(), "重试", () -> ack(category, recordId, action));
                });
            }
        }).start();
    }

    private void snooze(String category, long recordId, int hours) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            state.showEmpty("🔒", "未登录", "请先登录后再操作", "去登录", () -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再操作", "关闭", this::finish);
            return;
        }
        if (acting) {
            return;
        }
        acting = true;
        runOnUiThread(() -> {
            pb.setVisibility(android.view.View.VISIBLE);
            state.hide();
        });
        long until = System.currentTimeMillis() + hours * 60L * 60L * 1000L;
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("category", category);
                body.addProperty("recordId", recordId);
                body.addProperty("action", "snooze");
                body.addProperty("snoozeUntil", until);
                api.postJson("/api/events/ack", token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    Toast.makeText(this, "稍后提醒", Toast.LENGTH_SHORT).show();
                    load();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(android.view.View.GONE);
                    acting = false;
                    state.showError(e.getMessage(), "重试", () -> snooze(category, recordId, hours));
                });
            }
        }).start();
    }
}
