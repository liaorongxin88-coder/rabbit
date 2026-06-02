package com.rabbit.app;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.rabbit.app.notify.NotificationHelper;
import com.rabbit.app.storage.AppConfigStore;
import com.rabbit.app.worker.EventReminderWorker;

import java.util.concurrent.TimeUnit;

public class RabbitApp extends Application {
    private static RabbitApp instance;

    public static RabbitApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        AppConfigStore store = new AppConfigStore(this);
        String url = store.getBaseUrl();
        if (url != null && !url.trim().isEmpty()) {
            Config.setBaseUrl(url);
        }
        NotificationHelper.ensureChannel(this);
        scheduleEventReminder();
    }

    private void scheduleEventReminder() {
        Constraints c = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(EventReminderWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(c)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("event_reminder_worker", ExistingPeriodicWorkPolicy.KEEP, req);
    }
}
