package com.rabbit.app.storage;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionStore {
    private static final String SP_NAME = "rabbit_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_HOUSE_ID = "houseId";

    private final SharedPreferences sp;

    public SessionStore(Context context) {
        this.sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public void setToken(String token) {
        sp.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return sp.getString(KEY_TOKEN, null);
    }

    public void setUserName(String userName) {
        sp.edit().putString(KEY_USER_NAME, userName).apply();
    }

    public String getUserName() {
        return sp.getString(KEY_USER_NAME, null);
    }

    public void setUserId(long userId) {
        sp.edit().putLong(KEY_USER_ID, userId).apply();
    }

    public long getUserId() {
        return sp.getLong(KEY_USER_ID, 0L);
    }

    public void setHouseId(long houseId) {
        sp.edit().putLong(KEY_HOUSE_ID, houseId).apply();
    }

    public long getHouseId() {
        return sp.getLong(KEY_HOUSE_ID, 0L);
    }

    public void clear() {
        sp.edit().clear().apply();
    }
}
