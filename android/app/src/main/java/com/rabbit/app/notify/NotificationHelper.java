package com.rabbit.app.notify;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.rabbit.app.R;
import com.rabbit.app.ui.EventsActivity;

import java.util.List;

public class NotificationHelper {
    public static final String CHANNEL_ID = "event_reminders";

    public static boolean canPost(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
        if (ch != null) {
            return;
        }
        NotificationChannel x = new NotificationChannel(CHANNEL_ID, "事件提醒", NotificationManager.IMPORTANCE_DEFAULT);
        x.setDescription("养兔系统事件提醒");
        nm.createNotificationChannel(x);
    }

    public static void notifyEvents(Context ctx, long houseId, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String title = "兔舍" + houseId + "有" + lines.size() + "条待处理提醒";
        String text = lines.get(0);
        Intent it = new Intent(ctx, EventsActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 2001, it, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.BigTextStyle big = new NotificationCompat.BigTextStyle();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(lines.get(i));
            if (i >= 8) {
                if (lines.size() > 9) {
                    sb.append("\n... 共").append(lines.size()).append("条");
                }
                break;
            }
        }
        big.bigText(sb.toString());

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(big)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat nmc = NotificationManagerCompat.from(ctx);
        if (!nmc.areNotificationsEnabled()) {
            return;
        }
        nmc.notify(2001, b.build());
    }
}

