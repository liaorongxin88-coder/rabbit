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
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class CagesManageActivity extends AppCompatActivity {
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private TextView tvResult;
    private ListView lv;
    private ProgressBar pb;
    private StatePanel state;

    private ApiClient api;
    private SessionStore session;
    private TwoLineCardAdapter adapter;

    private final List<Long> cageIds = new ArrayList<Long>();
    private final List<CageRow> cages = new ArrayList<CageRow>();

    private static class CageRow {
        long id;
        String cageNumber;
        String status;
        int rabbitCount;
        boolean isFed;
        boolean isEnabled;
        String remark;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cages_manage);

        api = new ApiClient();
        session = new SessionStore(this);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);

        tvResult = findViewById(R.id.tvCageManageResult);
        lv = findViewById(R.id.lvCagesManage);
        pb = findViewById(R.id.pbCageManageLoading);
        state = new StatePanel(this);

        tvTopTitle.setText("笼位维护");
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.VISIBLE);
        btnTopRight.setText("操作");
        btnTopRight.setOnClickListener(v -> showTopActions());
        refreshHeader();
        HouseSwitchUtil.attach(this, tvTopHouse, api, session, id -> recreate());

        adapter = new TwoLineCardAdapter(this, new ArrayList<String>());
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= cages.size()) {
                return;
            }
            CageRow c = cages.get(position);
            showCageActions(c);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHeader();
        loadPermsAndData();
    }

    private void refreshHeader() {
        tvTopHouse.setText("用户：" + safe(session.getUserName()) + "  兔舍ID：" + session.getHouseId());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void loadPermsAndData() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty()) {
            pb.setVisibility(View.GONE);
            adapter.clear();
            adapter.notifyDataSetChanged();
            tvResult.setText("");
            state.showEmpty("🔒", "未登录", "请先登录后再维护笼位", "去登录", () -> {
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
            });
            return;
        }
        if (houseId <= 0) {
            pb.setVisibility(View.GONE);
            adapter.clear();
            adapter.notifyDataSetChanged();
            tvResult.setText("");
            state.showEmpty("🏠", "未选择兔舍", "选择兔舍后再维护笼位", "关闭", this::finish);
            return;
        }
        pb.setVisibility(View.VISIBLE);
        state.hide();
        new Thread(() -> {
            try {
                JsonObject perm = api.getJson("/api/houses/permission", token, houseId);
                JsonObject p = perm.has("data") && perm.get("data").isJsonObject() ? perm.getAsJsonObject("data") : new JsonObject();
                String perms = p.has("perms") ? p.get("perms").getAsString() : "";
                boolean isAdmin = p.has("isAdmin") && p.get("isAdmin").getAsBoolean();
                boolean canControl = isAdmin || "control".equalsIgnoreCase(perms);
                runOnUiThread(() -> {
                    btnTopRight.setEnabled(canControl);
                    if (!canControl) {
                        tvResult.setText("");
                        state.showEmpty("🔒", "权限不足", "当前账号无 control 权限，无法维护笼位", null, null);
                    } else {
                        tvResult.setText("");
                        state.hide();
                    }
                });

                JsonObject resp = api.getJson("/api/cages", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                parse(arr);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    if (btnTopRight.isEnabled()) {
                        render();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    state.showError("加载失败", e.getMessage(), "重试", v -> loadPermsAndData());
                });
            }
        }).start();
    }

    private void parse(JsonArray arr) {
        cages.clear();
        cageIds.clear();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            CageRow c = new CageRow();
            c.id = o.has("id") ? o.get("id").getAsLong() : 0L;
            c.cageNumber = o.has("cageNumber") && !o.get("cageNumber").isJsonNull() ? o.get("cageNumber").getAsString() : "";
            c.status = o.has("status") && !o.get("status").isJsonNull() ? o.get("status").getAsString() : "";
            c.rabbitCount = o.has("rabbitCount") && !o.get("rabbitCount").isJsonNull() ? o.get("rabbitCount").getAsInt() : 0;
            c.isFed = o.has("isFed") && !o.get("isFed").isJsonNull() && o.get("isFed").getAsBoolean();
            c.isEnabled = !o.has("isEnabled") || o.get("isEnabled").isJsonNull() || o.get("isEnabled").getAsBoolean();
            c.remark = o.has("remark") && !o.get("remark").isJsonNull() ? o.get("remark").getAsString() : "";
            cages.add(c);
            cageIds.add(c.id);
        }
    }

    private void render() {
        adapter.clear();
        List<String> rows = new ArrayList<String>();
        for (CageRow c : cages) {
            String tag = c.isEnabled ? "" : "status:停用";
            String title = "笼位 " + c.cageNumber + "||cage" + (tag.isEmpty() ? "" : (" " + tag));
            String sub = "id=" + c.id + "  兔数=" + c.rabbitCount + (c.isFed ? "  今日已喂" : "") + (c.remark == null || c.remark.isEmpty() ? "" : ("\n" + c.remark));
            rows.add(title + "\n" + sub);
        }
        if (rows.isEmpty()) {
            state.showEmpty("暂无笼位", "可在右上角新增笼位", "新增", v -> showAddDialog());
        } else {
            state.hide();
        }
        adapter.addAll(rows);
        adapter.notifyDataSetChanged();
    }

    private void showTopActions() {
        String[] items = new String[]{"新增笼位", "校准笼位兔数"};
        new AlertDialog.Builder(this)
                .setTitle("笼位维护")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        showAddDialog();
                    } else if (which == 1) {
                        recountRabbitCount();
                    }
                })
                .show();
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        box.setPadding(pad, pad, pad, pad);

        EditText etNumber = new EditText(this);
        etNumber.setHint("笼位号，例如 1-2-1");
        box.addView(etNumber);

        EditText etRemark = new EditText(this);
        etRemark.setHint("备注（可选）");
        box.addView(etRemark);

        new AlertDialog.Builder(this)
                .setTitle("新增笼位")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String num = etNumber.getText() == null ? "" : etNumber.getText().toString().trim();
                    String remark = etRemark.getText() == null ? "" : etRemark.getText().toString().trim();
                    if (num.isEmpty()) {
                        Toast.makeText(this, "笼位号不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    doCreate(num, remark);
                })
                .show();
    }

    private void doCreate(String cageNumber, String remark) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("cageNumber", cageNumber);
                body.addProperty("isEnabled", true);
                if (remark != null && !remark.isEmpty()) {
                    body.addProperty("remark", remark);
                }
                api.postJson("/api/cages", token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    loadPermsAndData();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showCageActions(CageRow c) {
        String t = c == null ? "" : c.cageNumber;
        String[] items = new String[]{"编辑", c.isEnabled ? "停用" : "启用", "纠错兔数", "删除"};
        new AlertDialog.Builder(this)
                .setTitle("笼位 " + t)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        showEditDialog(c);
                    } else if (which == 1) {
                        doToggleEnabled(c);
                    } else if (which == 2) {
                        showSetCountDialog(c);
                    } else if (which == 3) {
                        confirmDelete(c);
                    }
                })
                .show();
    }

    private void showEditDialog(CageRow c) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        box.setPadding(pad, pad, pad, pad);

        EditText etNumber = new EditText(this);
        etNumber.setHint("笼位号");
        etNumber.setText(c.cageNumber);
        box.addView(etNumber);

        EditText etRemark = new EditText(this);
        etRemark.setHint("备注（可选）");
        etRemark.setText(c.remark);
        box.addView(etRemark);

        new AlertDialog.Builder(this)
                .setTitle("编辑笼位")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String num = etNumber.getText() == null ? "" : etNumber.getText().toString().trim();
                    String remark = etRemark.getText() == null ? "" : etRemark.getText().toString().trim();
                    if (num.isEmpty()) {
                        Toast.makeText(this, "笼位号不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    doUpdate(c.id, num, remark, c.isEnabled);
                })
                .show();
    }

    private void doToggleEnabled(CageRow c) {
        if (c == null) {
            return;
        }
        doUpdate(c.id, c.cageNumber, c.remark, !c.isEnabled);
    }

    private void doUpdate(long cageId, String cageNumber, String remark, boolean isEnabled) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("cageNumber", cageNumber);
                body.addProperty("isEnabled", isEnabled);
                if (remark != null && !remark.isEmpty()) {
                    body.addProperty("remark", remark);
                }
                api.putJson("/api/cages/" + cageId, token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    loadPermsAndData();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showSetCountDialog(CageRow c) {
        EditText et = new EditText(this);
        et.setHint("输入兔数（>=0）");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(c.rabbitCount));
        new AlertDialog.Builder(this)
                .setTitle("纠错兔数")
                .setView(et)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String s = et.getText() == null ? "" : et.getText().toString().trim();
                    int v;
                    try {
                        v = Integer.parseInt(s);
                    } catch (Exception e) {
                        Toast.makeText(this, "数字不合法", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (v < 0) {
                        Toast.makeText(this, "不能小于0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    doSetCount(c.id, v);
                })
                .show();
    }

    private void doSetCount(long cageId, int rabbitCount) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("rabbitCount", rabbitCount);
                api.putJson("/api/cages/" + cageId + "/rabbit-count", token, houseId, body);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    loadPermsAndData();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void confirmDelete(CageRow c) {
        new AlertDialog.Builder(this)
                .setTitle("删除笼位")
                .setMessage("确认删除笼位 " + c.cageNumber + "？（需确保笼内无在栏兔子）")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> doDelete(c.id))
                .show();
    }

    private void doDelete(long cageId) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                api.deleteJson("/api/cages/" + cageId, token, houseId);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    loadPermsAndData();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void recountRabbitCount() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        pb.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                api.postJson("/api/cages/recount-rabbit-count", token, houseId, new JsonObject());
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    Toast.makeText(this, "已校准笼位兔数", Toast.LENGTH_SHORT).show();
                    loadPermsAndData();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
