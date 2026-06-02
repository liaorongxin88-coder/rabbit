package com.rabbit.app.ui;

import android.content.Intent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.storage.SessionStore;

import java.util.ArrayList;
import java.util.List;

public class HouseSwitchUtil {
    public interface OnHouseSwitched {
        void onSwitched(long newHouseId);
    }

    public static void attach(AppCompatActivity activity, TextView tvTopHouse, ApiClient api, SessionStore session) {
        attach(activity, tvTopHouse, api, session, null);
    }

    public static void attach(AppCompatActivity activity, TextView tvTopHouse, ApiClient api, SessionStore session, OnHouseSwitched onSwitched) {
        if (activity == null || tvTopHouse == null || api == null || session == null) {
            return;
        }
        tvTopHouse.setOnClickListener(v -> {
            String token = session.getToken();
            long current = session.getHouseId();
            if (token == null || token.trim().isEmpty()) {
                Toast.makeText(activity, "未登录", Toast.LENGTH_SHORT).show();
                return;
            }
            Runnable load = new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, "加载兔舍列表...", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        try {
                            JsonObject resp = api.getJson("/api/houses", token, null);
                            JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
                            List<Long> ids = new ArrayList<Long>();
                            List<String> labels = new ArrayList<String>();
                            for (int i = 0; i < arr.size(); i++) {
                                JsonObject o = arr.get(i).getAsJsonObject();
                                long id = o.has("id") ? o.get("id").getAsLong() : 0L;
                                String name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : "";
                                if (id <= 0) {
                                    continue;
                                }
                                ids.add(id);
                                labels.add((id == current ? "【当前】" : "") + "兔舍#" + id + " " + name);
                            }
                            activity.runOnUiThread(() -> {
                                if (ids.isEmpty()) {
                                    new AlertDialog.Builder(activity)
                                            .setTitle("没有可用兔舍")
                                            .setMessage("请先创建兔舍，然后再切换")
                                            .setNegativeButton("取消", null)
                                            .setPositiveButton("去创建", (d, which) -> activity.startActivity(new Intent(activity, CreateHouseActivity.class)))
                                            .show();
                                    return;
                                }
                                new AlertDialog.Builder(activity)
                                        .setTitle("切换兔舍")
                                        .setItems(labels.toArray(new String[0]), (d, which) -> {
                                            if (which >= 0 && which < ids.size()) {
                                                long id = ids.get(which);
                                                session.setHouseId(id);
                                                Toast.makeText(activity, "已切换兔舍#" + id, Toast.LENGTH_SHORT).show();
                                                if (onSwitched != null) {
                                                    onSwitched.onSwitched(id);
                                                } else {
                                                    activity.recreate();
                                                }
                                            }
                                        })
                                        .show();
                            });
                        } catch (Exception e) {
                            String msg = e.getMessage() == null ? "加载失败" : e.getMessage();
                            activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                                    .setTitle("加载兔舍失败")
                                    .setMessage(msg)
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("重试", (d, which) -> run())
                                    .show());
                        }
                    }).start();
                }
            };
            load.run();
        });
    }
}
