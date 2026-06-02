package com.rabbit.app.ui;

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.rabbit.app.R;

public class StatePanel {
    private final View panel;
    private final TextView tvEmoji;
    private final TextView tvTitle;
    private final TextView tvMessage;
    private final MaterialButton btnAction;

    public StatePanel(AppCompatActivity activity) {
        panel = activity.findViewById(R.id.statePanel);
        tvEmoji = activity.findViewById(R.id.tvStateEmoji);
        tvTitle = activity.findViewById(R.id.tvStateTitle);
        tvMessage = activity.findViewById(R.id.tvStateMessage);
        btnAction = activity.findViewById(R.id.btnStateAction);
    }

    public void hide() {
        if (panel != null) {
            panel.setVisibility(View.GONE);
        }
    }

    public void showEmpty(String emoji, String title, String message, String actionText, Runnable action) {
        show(emoji == null || emoji.isEmpty() ? "🐇" : emoji, title, message, actionText, action);
    }

    public void showEmpty(String title, String message, String actionText, OnClickListener action) {
        show("🐇", title, message, actionText, action);
    }

    public void showError(String message, String actionText, Runnable action) {
        show("⚠", "加载失败", message, actionText, action);
    }

    public void showError(String title, String message, String actionText, OnClickListener action) {
        show("⚠", title, message, actionText, action);
    }

    private void show(String emoji, String title, String message, String actionText, Runnable action) {
        show(emoji, title, message, actionText, action == null ? null : v -> action.run());
    }

    private void show(String emoji, String title, String message, String actionText, OnClickListener action) {
        if (panel == null) {
            return;
        }
        panel.setVisibility(View.VISIBLE);
        if (tvEmoji != null) {
            tvEmoji.setText(emoji == null ? "" : emoji);
        }
        if (tvTitle != null) {
            tvTitle.setText(title == null ? "" : title);
        }
        if (tvMessage != null) {
            tvMessage.setText(message == null ? "" : message);
        }
        if (btnAction != null) {
            if (actionText == null || actionText.trim().isEmpty() || action == null) {
                btnAction.setVisibility(View.GONE);
                btnAction.setOnClickListener(null);
            } else {
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText(actionText);
                btnAction.setOnClickListener(action);
            }
        }
    }
}
