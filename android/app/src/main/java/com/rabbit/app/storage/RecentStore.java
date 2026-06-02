package com.rabbit.app.storage;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RecentStore {
    private static final String SP_NAME = "rabbit_recent";
    private final SharedPreferences sp;

    public RecentStore(Context context) {
        this.sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public void push(String key, long id, int limit) {
        if (key == null || key.trim().isEmpty() || id <= 0) {
            return;
        }
        List<Long> cur = getIds(key);
        LinkedHashSet<Long> set = new LinkedHashSet<Long>();
        set.add(id);
        for (Long v : cur) {
            if (v != null && v > 0) {
                set.add(v);
            }
            if (set.size() >= limit) {
                break;
            }
        }
        save(key, set);
    }

    public List<Long> getIds(String key) {
        Set<String> raw = sp.getStringSet(key, null);
        List<Long> res = new ArrayList<Long>();
        if (raw == null) {
            return res;
        }
        for (String s : raw) {
            try {
                res.add(Long.parseLong(s));
            } catch (Exception ignored) {
            }
        }
        return res;
    }

    private void save(String key, Set<Long> ids) {
        LinkedHashSet<String> raw = new LinkedHashSet<String>();
        for (Long v : ids) {
            if (v != null && v > 0) {
                raw.add(String.valueOf(v));
            }
        }
        sp.edit().putStringSet(key, raw).apply();
    }
}

