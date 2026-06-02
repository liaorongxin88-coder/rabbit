package com.rabbit.app.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

public class NfcTagCacheStore {
    private static final String SP_NAME = "rabbit_nfc_cache";

    private final SharedPreferences sp;
    private final Gson gson;

    public NfcTagCacheStore(Context context) {
        this.sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public synchronized void put(String tagUid, NfcCachedTarget target) {
        if (tagUid == null || tagUid.trim().isEmpty() || target == null) {
            return;
        }
        target.setCacheTime(System.currentTimeMillis());
        String json = gson.toJson(target);
        sp.edit().putString(tagUid.trim(), json).apply();
    }

    public synchronized NfcCachedTarget get(String tagUid) {
        if (tagUid == null || tagUid.trim().isEmpty()) {
            return null;
        }
        try {
            String json = sp.getString(tagUid.trim(), null);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            return gson.fromJson(json, NfcCachedTarget.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized void remove(String tagUid) {
        if (tagUid == null || tagUid.trim().isEmpty()) {
            return;
        }
        sp.edit().remove(tagUid.trim()).apply();
    }
}
