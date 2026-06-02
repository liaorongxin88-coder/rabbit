package com.rabbit.app.ui;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import com.rabbit.app.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PickerDialogUtil {
    public static class PickItem {
        public long id;
        public String label;
        public String searchKey;

        public PickItem(long id, String label, String searchKey) {
            this.id = id;
            this.label = label;
            this.searchKey = searchKey;
        }
    }

    public interface OnPick {
        void onPick(PickItem item);
    }

    public interface OnPickMulti {
        void onPick(List<PickItem> items);
    }

    public static void showSingle(Activity act, String title, List<PickItem> items, List<Long> recentIds, OnPick onPick) {
        List<PickItem> ordered = orderByRecent(items, recentIds);
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_picker, null);
        EditText etSearch = v.findViewById(R.id.etPickerSearch);
        ListView lv = v.findViewById(R.id.lvPickerItems);

        List<PickItem> filtered = new ArrayList<PickItem>(ordered);
        List<String> labels = new ArrayList<String>();
        for (PickItem it : filtered) {
            labels.add(it.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(act, android.R.layout.simple_list_item_1, labels);
        lv.setAdapter(adapter);

        Runnable applyFilter = () -> {
            String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
            filtered.clear();
            labels.clear();
            for (PickItem it : ordered) {
                if (q.isEmpty()) {
                    filtered.add(it);
                    labels.add(it.label);
                    continue;
                }
                String k = it.searchKey == null ? "" : it.searchKey;
                if (k.toLowerCase(Locale.getDefault()).contains(q) || String.valueOf(it.id).contains(q)) {
                    filtered.add(it);
                    labels.add(it.label);
                }
            }
            adapter.notifyDataSetChanged();
        };

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle(title)
                .setView(v)
                .setNegativeButton("取消", null)
                .create();

        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < filtered.size()) {
                if (onPick != null) {
                    onPick.onPick(filtered.get(position));
                }
            }
            dlg.dismiss();
        });

        dlg.show();
    }

    public static void showMulti(Activity act, String title, List<PickItem> items, List<Long> recentIds, List<Long> preCheckedIds, OnPickMulti onPick) {
        List<PickItem> ordered = orderByRecent(items, recentIds);
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_picker, null);
        EditText etSearch = v.findViewById(R.id.etPickerSearch);
        ListView lv = v.findViewById(R.id.lvPickerItems);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        List<PickItem> filtered = new ArrayList<PickItem>(ordered);
        List<String> labels = new ArrayList<String>();
        for (PickItem it : filtered) {
            labels.add(it.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(act, android.R.layout.simple_list_item_multiple_choice, labels);
        lv.setAdapter(adapter);

        Set<Long> checked = new HashSet<Long>();
        if (preCheckedIds != null) {
            checked.addAll(preCheckedIds);
        }
        for (int i = 0; i < filtered.size(); i++) {
            if (checked.contains(filtered.get(i).id)) {
                lv.setItemChecked(i, true);
            }
        }

        Runnable applyFilter = () -> {
            String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
            Set<Long> keep = new HashSet<Long>();
            for (int i = 0; i < filtered.size(); i++) {
                if (lv.isItemChecked(i)) {
                    keep.add(filtered.get(i).id);
                }
            }
            filtered.clear();
            labels.clear();
            for (PickItem it : ordered) {
                if (q.isEmpty()) {
                    filtered.add(it);
                    labels.add(it.label);
                    continue;
                }
                String k = it.searchKey == null ? "" : it.searchKey;
                if (k.toLowerCase(Locale.getDefault()).contains(q) || String.valueOf(it.id).contains(q)) {
                    filtered.add(it);
                    labels.add(it.label);
                }
            }
            adapter.notifyDataSetChanged();
            for (int i = 0; i < filtered.size(); i++) {
                if (keep.contains(filtered.get(i).id)) {
                    lv.setItemChecked(i, true);
                }
            }
        };

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle(title)
                .setView(v)
                .setPositiveButton("确定", (d, which) -> {
                    List<PickItem> res = new ArrayList<PickItem>();
                    for (int i = 0; i < filtered.size(); i++) {
                        if (lv.isItemChecked(i)) {
                            res.add(filtered.get(i));
                        }
                    }
                    if (onPick != null) {
                        onPick.onPick(res);
                    }
                })
                .setNegativeButton("取消", null)
                .create();

        dlg.show();
    }

    private static List<PickItem> orderByRecent(List<PickItem> items, List<Long> recentIds) {
        List<PickItem> res = new ArrayList<PickItem>();
        Set<Long> recent = new HashSet<Long>();
        if (recentIds != null) {
            recent.addAll(recentIds);
        }
        if (!recent.isEmpty()) {
            for (Long id : recentIds) {
                if (id == null) {
                    continue;
                }
                for (PickItem it : items) {
                    if (it != null && it.id == id) {
                        res.add(it);
                        break;
                    }
                }
            }
        }
        for (PickItem it : items) {
            if (it == null) {
                continue;
            }
            if (recent.contains(it.id)) {
                continue;
            }
            res.add(it);
        }
        return res;
    }
}

