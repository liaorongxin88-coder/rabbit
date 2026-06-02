package com.rabbit.app.notify;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rabbit.app.net.Json;

import java.util.HashMap;
import java.util.Map;

public class EventNotifyStore {
    private final SharedPreferences sp;

    public EventNotifyStore(Context ctx) {
        this.sp = ctx.getSharedPreferences("event_notify_store", Context.MODE_PRIVATE);
    }

    public Map<String, Long> loadMap() {
        String s = sp.getString("notified_map", null);
        Map<String, Long> map = new HashMap<String, Long>();
        if (s == null || s.trim().isEmpty()) {
            return map;
        }
        try {
            JsonObject obj = Json.gson.fromJson(s, JsonObject.class);
            if (obj == null) {
                return map;
            }
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String k = e.getKey();
                if (k == null) {
                    continue;
                }
                long v = e.getValue() != null && !e.getValue().isJsonNull() ? e.getValue().getAsLong() : 0L;
                map.put(k, v);
            }
            return map;
        } catch (Exception ignored) {
            return new HashMap<String, Long>();
        }
    }

    public void saveMap(Map<String, Long> map) {
        JsonObject obj = new JsonObject();
        if (map != null) {
            for (Map.Entry<String, Long> e : map.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                obj.addProperty(e.getKey(), e.getValue() == null ? 0L : e.getValue());
            }
        }
        sp.edit().putString("notified_map", obj.toString()).apply();
    }
}

