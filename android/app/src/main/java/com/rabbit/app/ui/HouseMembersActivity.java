package com.rabbit.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.UUID;

public class HouseMembersActivity extends AppCompatActivity {
    private TextView tvInfo;
    private TextView tvResult;
    private Button btnAdd;
    private Button btnRefresh;
    private ListView lv;
    private TextView tvTopTitle;
    private TextView tvTopHouse;
    private Button btnTopBack;
    private Button btnTopRight;

    private ApiClient api;
    private SessionStore session;

    private final List<Long> memberUserIds = new ArrayList<Long>();
    private final List<String> memberUserNames = new ArrayList<String>();
    private final List<String> memberPerms = new ArrayList<String>();
    private final List<Boolean> memberAdmins = new ArrayList<Boolean>();
    private final List<String> memberJoinTimes = new ArrayList<String>();
    private final List<String> displayList = new ArrayList<String>();
    private TwoLineCardAdapter adapter;

    private String myPerms = "";
    private boolean myAdmin = false;
    private boolean canControl = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_house_members);

        api = new ApiClient();
        session = new SessionStore(this);

        tvInfo = findViewById(R.id.tvMembersInfo);
        tvResult = findViewById(R.id.tvMembersResult);
        btnAdd = findViewById(R.id.btnMembersAdd);
        btnRefresh = findViewById(R.id.btnMembersRefresh);
        lv = findViewById(R.id.lvMembers);

        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopHouse = findViewById(R.id.tvTopHouse);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRight = findViewById(R.id.btnTopRight);
        tvTopTitle.setText("成员与权限");
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
        btnTopBack.setOnClickListener(v -> finish());
        btnTopRight.setVisibility(View.GONE);
        HouseSwitchUtil.attach(this, tvTopHouse, api, session);

        adapter = new TwoLineCardAdapter(this, displayList);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> onMemberClick(position));

        btnRefresh.setOnClickListener(v -> loadAll());
        btnAdd.setOnClickListener(v -> showAddDialog());

        loadAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvTopHouse.setText("兔舍ID：" + session.getHouseId());
    }

    private void loadAll() {
        tvResult.setText("");
        if (session.getHouseId() <= 0) {
            tvInfo.setText("请先选择兔舍");
            displayList.clear();
            adapter.notifyDataSetChanged();
            btnAdd.setEnabled(false);
            return;
        }
        loadMyPermissionThenMembers();
    }

    private void loadMyPermissionThenMembers() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/houses/permission", token, houseId);
                JsonObject data = resp.has("data") && resp.get("data").isJsonObject() ? resp.getAsJsonObject("data") : new JsonObject();
                String perms = data.has("perms") && !data.get("perms").isJsonNull() ? data.get("perms").getAsString() : "";
                boolean isAdmin = data.has("isAdmin") && !data.get("isAdmin").isJsonNull() && data.get("isAdmin").getAsBoolean();
                myPerms = perms;
                myAdmin = isAdmin;
                canControl = myAdmin || "control".equals(myPerms);
                runOnUiThread(() -> {
                    tvInfo.setText("我的权限：" + formatPerm(myPerms, myAdmin));
                    btnAdd.setEnabled(canControl);
                    if (!canControl) {
                        btnAdd.setText("新增成员（需管理权限）");
                    } else {
                        btnAdd.setText("新增成员");
                    }
                });
                if (!canControl) {
                    runOnUiThread(() -> {
                        displayList.clear();
                        adapter.notifyDataSetChanged();
                        tvResult.setText("没有管理权限，无法查看成员列表");
                    });
                    return;
                }
                loadMembers();
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvInfo.setText("我的权限：获取失败 " + e.getMessage());
                    displayList.clear();
                    adapter.notifyDataSetChanged();
                    btnAdd.setEnabled(false);
                });
            }
        }).start();
    }

    private void loadMembers() {
        String token = session.getToken();
        long houseId = session.getHouseId();
        new Thread(() -> {
            try {
                JsonObject resp = api.getJson("/api/house-members", token, houseId);
                JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();

                List<Long> ids = new ArrayList<Long>();
                List<String> names = new ArrayList<String>();
                List<String> perms = new ArrayList<String>();
                List<Boolean> admins = new ArrayList<Boolean>();
                List<String> joins = new ArrayList<String>();
                List<String> display = new ArrayList<String>();
                long myUserId = session.getUserId();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    long uid = o.has("userId") ? o.get("userId").getAsLong() : 0L;
                    String userName = o.has("userName") && !o.get("userName").isJsonNull() ? o.get("userName").getAsString() : "";
                    String p = o.has("perms") && !o.get("perms").isJsonNull() ? o.get("perms").getAsString() : "";
                    boolean a = o.has("isAdmin") && !o.get("isAdmin").isJsonNull() && o.get("isAdmin").getAsBoolean();
                    String jt = "";
                    if (o.has("joinTime") && !o.get("joinTime").isJsonNull()) {
                        jt = o.get("joinTime").getAsString();
                    }
                    ids.add(uid);
                    names.add(userName);
                    perms.add(p);
                    admins.add(a);
                    joins.add(jt);

                    String line1 = userName + " (#" + uid + ")";
                    if (uid == myUserId) {
                        line1 = line1 + " (我)";
                    }
                    if (a) {
                        line1 = line1 + " [管理员]";
                    }
                    String line2 = "权限：" + formatPerm(p, a) + "  加入：" + TimeUtil.fmtAny(jt);
                    display.add(line1 + "\n" + line2);
                }
                runOnUiThread(() -> {
                    memberUserIds.clear();
                    memberUserNames.clear();
                    memberPerms.clear();
                    memberAdmins.clear();
                    memberJoinTimes.clear();
                    displayList.clear();
                    memberUserIds.addAll(ids);
                    memberUserNames.addAll(names);
                    memberPerms.addAll(perms);
                    memberAdmins.addAll(admins);
                    memberJoinTimes.addAll(joins);
                    displayList.addAll(display);
                    adapter.notifyDataSetChanged();
                    tvResult.setText("成员数：" + displayList.size());
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvResult.setText("加载失败：" + e.getMessage()));
            }
        }).start();
    }

    private void onMemberClick(int position) {
        if (!canControl) {
            Toast.makeText(this, "需要管理权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (position < 0 || position >= memberUserIds.size()) {
            return;
        }
        long uid = memberUserIds.get(position);
        String name = position < memberUserNames.size() ? memberUserNames.get(position) : "";
        String[] items;
        if (uid == session.getUserId()) {
            items = new String[]{"编辑权限"};
        } else {
            items = new String[]{"编辑权限", "移除成员"};
        }
        new AlertDialog.Builder(this)
                .setTitle(name + " (#" + uid + ")")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        showUpdateDialog(position);
                    } else if (which == 1) {
                        confirmRemove(uid, name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddDialog() {
        if (!canControl) {
            Toast.makeText(this, "需要管理权限", Toast.LENGTH_SHORT).show();
            return;
        }
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_house_member, null);
        EditText etName = v.findViewById(R.id.etMemberUserName);
        Spinner spPerms = v.findViewById(R.id.spMemberPerms);
        CheckBox cbAdmin = v.findViewById(R.id.cbMemberAdmin);

        ArrayAdapter<String> permsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{"view", "edit", "control"});
        permsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPerms.setAdapter(permsAdapter);
        spPerms.setSelection(0);
        cbAdmin.setChecked(false);

        new AlertDialog.Builder(this)
                .setTitle("新增成员")
                .setView(v)
                .setPositiveButton("确定", (d, w) -> {
                    String userName = etName.getText().toString().trim();
                    String perms = (String) spPerms.getSelectedItem();
                    boolean isAdmin = cbAdmin.isChecked();
                    if (userName.isEmpty()) {
                        tvResult.setText("用户名不能为空");
                        return;
                    }
                    doAddMember(userName, perms, isAdmin);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showUpdateDialog(int position) {
        if (position < 0 || position >= memberUserIds.size()) {
            return;
        }
        long uid = memberUserIds.get(position);
        String name = memberUserNames.get(position);
        String perms = memberPerms.get(position);
        boolean isAdmin = memberAdmins.get(position) != null && memberAdmins.get(position);

        View v = LayoutInflater.from(this).inflate(R.layout.dialog_house_member, null);
        EditText etName = v.findViewById(R.id.etMemberUserName);
        Spinner spPerms = v.findViewById(R.id.spMemberPerms);
        CheckBox cbAdmin = v.findViewById(R.id.cbMemberAdmin);

        etName.setText(name);
        etName.setEnabled(false);
        ArrayAdapter<String> permsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{"view", "edit", "control"});
        permsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPerms.setAdapter(permsAdapter);
        spPerms.setSelection("edit".equals(perms) ? 1 : ("control".equals(perms) ? 2 : 0));
        cbAdmin.setChecked(isAdmin);

        new AlertDialog.Builder(this)
                .setTitle("编辑权限")
                .setView(v)
                .setPositiveButton("确定", (d, w) -> {
                    String newPerms = (String) spPerms.getSelectedItem();
                    boolean newAdmin = cbAdmin.isChecked();
                    doUpdateMember(uid, newPerms, newAdmin);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmRemove(long memberUserId, String name) {
        new AlertDialog.Builder(this)
                .setTitle("移除成员")
                .setMessage("确认移除 " + name + " (#" + memberUserId + ") ?")
                .setPositiveButton("移除", (d, w) -> doRemoveMember(memberUserId))
                .setNegativeButton("取消", null)
                .show();
    }

    private void doAddMember(String userName, String perms, boolean isAdmin) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        tvResult.setText("提交中...");
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("userName", userName);
                body.addProperty("perms", perms);
                body.addProperty("isAdmin", isAdmin);
                body.addProperty("requestId", UUID.randomUUID().toString());
                api.postJson("/api/house-members", token, houseId, body);
                runOnUiThread(() -> {
                    tvResult.setText("新增成功");
                    loadAll();
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvResult.setText("新增失败：" + e.getMessage()));
            }
        }).start();
    }

    private void doUpdateMember(long memberUserId, String perms, boolean isAdmin) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        tvResult.setText("提交中...");
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("perms", perms);
                body.addProperty("isAdmin", isAdmin);
                body.addProperty("requestId", UUID.randomUUID().toString());
                api.putJson("/api/house-members/" + memberUserId, token, houseId, body);
                runOnUiThread(() -> {
                    tvResult.setText("更新成功");
                    loadAll();
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvResult.setText("更新失败：" + e.getMessage()));
            }
        }).start();
    }

    private void doRemoveMember(long memberUserId) {
        String token = session.getToken();
        long houseId = session.getHouseId();
        tvResult.setText("提交中...");
        new Thread(() -> {
            try {
                String requestId = UUID.randomUUID().toString();
                api.deleteJson("/api/house-members/" + memberUserId + "?requestId=" + requestId, token, houseId);
                runOnUiThread(() -> {
                    tvResult.setText("移除成功");
                    loadAll();
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvResult.setText("移除失败：" + e.getMessage()));
            }
        }).start();
    }

    private String formatPerm(String perms, boolean isAdmin) {
        if (isAdmin) {
            return "管理员";
        }
        if ("control".equals(perms)) {
            return "可管理(control)";
        }
        if ("edit".equals(perms)) {
            return "可编辑(edit)";
        }
        return "只读(view)";
    }
}

