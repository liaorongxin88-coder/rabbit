package com.rabbit.app.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rabbit.app.net.ApiClient;
import com.rabbit.app.notify.EventNotifyStore;
import com.rabbit.app.notify.NotificationHelper;
import com.rabbit.app.storage.SessionStore;
import com.rabbit.app.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventReminderWorker extends Worker {
    public EventReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        if (!NotificationHelper.canPost(ctx)) {
            return Result.success();
        }
        SessionStore session = new SessionStore(ctx);
        String token = session.getToken();
        long houseId = session.getHouseId();
        if (token == null || token.trim().isEmpty() || houseId <= 0) {
            return Result.success();
        }
        try {
            ApiClient api = new ApiClient();
            JsonObject resp = api.getJson("/api/events?onlyUnnotified=true", token, houseId);
            JsonArray arr = resp.has("data") && resp.get("data").isJsonArray() ? resp.getAsJsonArray("data") : new JsonArray();
            EventNotifyStore store = new EventNotifyStore(ctx);
            Map<String, Long> notified = store.loadMap();
            Set<String> current = new HashSet<String>();
            List<String> newLines = new ArrayList<String>();
            long now = System.currentTimeMillis();

            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i).isJsonObject()) {
                    continue;
                }
                JsonObject o = arr.get(i).getAsJsonObject();
                long recordId = o.has("recordId") && !o.get("recordId").isJsonNull() ? o.get("recordId").getAsLong() : 0L;
                String category = o.has("category") && !o.get("category").isJsonNull() ? o.get("category").getAsString() : "";
                String eventType = o.has("eventType") && !o.get("eventType").isJsonNull() ? o.get("eventType").getAsString() : "";
                String eventDate = o.has("eventDate") && !o.get("eventDate").isJsonNull() ? TimeUtil.fmtAny(o.get("eventDate").getAsString()) : "";
                long rabbitId = o.has("rabbitId") && !o.get("rabbitId").isJsonNull() ? o.get("rabbitId").getAsLong() : 0L;
                if (recordId <= 0 || category == null || category.trim().isEmpty()) {
                    continue;
                }
                String key = category + ":" + recordId;
                current.add(key);
                if (!notified.containsKey(key)) {
                    String line = category + " · " + safe(eventType) + " · " + safe(eventDate) + (rabbitId > 0 ? " · 兔" + rabbitId : "");
                    newLines.add(line);
                    notified.put(key, now);
                }
            }

            Iterator<String> it = notified.keySet().iterator();
            while (it.hasNext()) {
                String k = it.next();
                if (!current.contains(k)) {
                    it.remove();
                }
            }
            store.saveMap(notified);

            if (!newLines.isEmpty()) {
                NotificationHelper.ensureChannel(ctx);
                NotificationHelper.notifyEvents(ctx, houseId, newLines);
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }
}
