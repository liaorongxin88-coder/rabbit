package com.rabbit.app.storage;

import android.content.Context;
import android.content.SharedPreferences;

public class AppConfigStore {
    private static final String SP_NAME = "rabbit_config";
    private static final String KEY_BASE_URL = "baseUrl";

    private final SharedPreferences sp;

    public AppConfigStore(Context context) {
        this.sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public void setBaseUrl(String url) {
        sp.edit().putString(KEY_BASE_URL, url).apply();
    }

    public String getBaseUrl() {
        return sp.getString(KEY_BASE_URL, null);
    }
}

