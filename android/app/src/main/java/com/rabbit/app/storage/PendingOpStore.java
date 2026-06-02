package com.rabbit.app.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PendingOpStore {
    private static final String SP_NAME = "rabbit_pending_ops";
    private static final String KEY_LIST = "ops";

    private final SharedPreferences sp;
    private final Gson gson;
    private final Type listType;

    public PendingOpStore(Context context) {
        this.sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.listType = new TypeToken<List<PendingOp>>() {
        }.getType();
    }

    public synchronized List<PendingOp> list() {
        String s = sp.getString(KEY_LIST, "[]");
        List<PendingOp> ops = gson.fromJson(s, listType);
        return ops == null ? new ArrayList<PendingOp>() : ops;
    }

    public synchronized int size() {
        return list().size();
    }

    public synchronized void add(PendingOp op) {
        List<PendingOp> ops = list();
        ops.add(0, op);
        save(ops);
    }

    public synchronized void remove(String id) {
        List<PendingOp> ops = list();
        Iterator<PendingOp> it = ops.iterator();
        while (it.hasNext()) {
            PendingOp op = it.next();
            if (op != null && id != null && id.equals(op.getId())) {
                it.remove();
                break;
            }
        }
        save(ops);
    }

    public synchronized void replaceAll(List<PendingOp> ops) {
        save(ops);
    }

    private void save(List<PendingOp> ops) {
        String s = gson.toJson(ops);
        sp.edit().putString(KEY_LIST, s).apply();
    }
}
