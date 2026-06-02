package com.rabbit.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.rabbit.app.R;

import java.util.List;

public class TwoLineCardAdapter extends ArrayAdapter<String> {
    public TwoLineCardAdapter(@NonNull Context context, @NonNull List<String> objects) {
        super(context, 0, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(getContext()).inflate(R.layout.item_card_two_line, parent, false);
        }
        TextView tv1 = v.findViewById(R.id.tvLine1);
        TextView tv2 = v.findViewById(R.id.tvLine2);
        TextView tvTag = v.findViewById(R.id.tvTag);
        View vAccent = v.findViewById(R.id.vAccent);
        String s = getItem(position);
        if (s == null) {
            tv1.setText("");
            tv2.setText("");
            if (tvTag != null) {
                tvTag.setVisibility(View.GONE);
            }
            bindAccent(vAccent, null);
            return v;
        }
        int idx = s.indexOf("\n");
        String line1 = idx >= 0 ? s.substring(0, idx) : s;
        String line2 = idx >= 0 ? s.substring(idx + 1) : "";
        String title = line1;
        String tag = null;
        int tIdx = line1.indexOf("||");
        if (tIdx >= 0) {
            title = line1.substring(0, tIdx);
            tag = line1.substring(tIdx + 2);
        }
        tv1.setText(title);
        tv2.setText(line2);
        TagParts parts = parseTag(tag);
        bindTag(tvTag, parts);
        bindAccent(vAccent, parts);
        return v;
    }

    private TagParts parseTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return null;
        }
        String raw = tag.trim();
        String type = raw;
        String level = null;
        String label = raw;
        int colon = raw.indexOf(":");
        String head = colon >= 0 ? raw.substring(0, colon) : raw;
        label = colon >= 0 ? raw.substring(colon + 1) : raw;
        int bang = head.indexOf("!");
        if (bang >= 0) {
            type = head.substring(0, bang);
            level = head.substring(bang + 1);
        } else {
            type = head;
        }
        if ("status".equalsIgnoreCase(type) && (level == null || level.isEmpty())) {
            String l = label == null ? "" : label;
            if (l.contains("逾期") || l.contains("超时") || l.contains("不足")) {
                level = "danger";
            } else if (l.contains("待") || l.contains("未")) {
                level = "warning";
            } else if (l.contains("已") || l.contains("完成") || l.contains("正常")) {
                level = "success";
            }
        }
        TagParts parts = new TagParts();
        parts.type = type;
        parts.level = level;
        parts.label = label;
        return parts;
    }

    private void bindTag(TextView tvTag, TagParts parts) {
        if (tvTag == null) {
            return;
        }
        if (parts == null) {
            tvTag.setVisibility(View.GONE);
            return;
        }
        tvTag.setText(parts.label == null ? "" : parts.label);
        tvTag.setVisibility(View.VISIBLE);

        int bg = R.drawable.bg_tag_neutral;
        int color = R.color.rabbit_text_secondary;
        if (parts.level != null && !parts.level.isEmpty()) {
            if ("success".equalsIgnoreCase(parts.level)) {
                bg = R.drawable.bg_tag_success;
                color = R.color.rabbit_success;
            } else if ("warning".equalsIgnoreCase(parts.level)) {
                bg = R.drawable.bg_tag_warning;
                color = R.color.rabbit_warning;
            } else if ("danger".equalsIgnoreCase(parts.level)) {
                bg = R.drawable.bg_tag_danger;
                color = R.color.rabbit_danger;
            }
        } else if ("batch".equalsIgnoreCase(parts.type)) {
            bg = R.drawable.bg_tag_batch;
            color = R.color.rabbit_mod_batch;
        } else if ("feed".equalsIgnoreCase(parts.type)) {
            bg = R.drawable.bg_tag_feed;
            color = R.color.rabbit_mod_feed;
        } else if ("treatment".equalsIgnoreCase(parts.type)) {
            bg = R.drawable.bg_tag_treatment;
            color = R.color.rabbit_mod_treatment;
        } else if ("inventory".equalsIgnoreCase(parts.type)) {
            bg = R.drawable.bg_tag_inventory;
            color = R.color.rabbit_mod_inventory;
        } else if ("sale".equalsIgnoreCase(parts.type)) {
            bg = R.drawable.bg_tag_sale;
            color = R.color.rabbit_mod_sale;
        }
        tvTag.setBackgroundResource(bg);
        tvTag.setTextColor(ContextCompat.getColor(getContext(), color));
    }

    private void bindAccent(View vAccent, TagParts parts) {
        if (vAccent == null) {
            return;
        }
        int color = R.color.rabbit_accent;
        if (parts != null) {
            if (parts.level != null && !parts.level.isEmpty()) {
                if ("success".equalsIgnoreCase(parts.level)) {
                    color = R.color.rabbit_success;
                } else if ("warning".equalsIgnoreCase(parts.level)) {
                    color = R.color.rabbit_warning;
                } else if ("danger".equalsIgnoreCase(parts.level)) {
                    color = R.color.rabbit_danger;
                }
            } else if ("batch".equalsIgnoreCase(parts.type)) {
                color = R.color.rabbit_mod_batch;
            } else if ("feed".equalsIgnoreCase(parts.type)) {
                color = R.color.rabbit_mod_feed;
            } else if ("treatment".equalsIgnoreCase(parts.type)) {
                color = R.color.rabbit_mod_treatment;
            } else if ("inventory".equalsIgnoreCase(parts.type)) {
                color = R.color.rabbit_mod_inventory;
            } else if ("sale".equalsIgnoreCase(parts.type)) {
                color = R.color.rabbit_mod_sale;
            }
        }
        vAccent.setBackgroundColor(ContextCompat.getColor(getContext(), color));
    }

    private static class TagParts {
        String type;
        String level;
        String label;
    }
}
