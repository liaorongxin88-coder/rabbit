package com.rabbit.app.modules.auth.job;

import com.rabbit.app.modules.auth.mapper.PhoneOneTapAttemptMapper;
import com.rabbit.app.modules.auth.mapper.PhoneOneTapRateBucketMapper;
import java.time.Clock;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PhoneOneTapCleanupJob {
    private static final int DELETE_BATCH_SIZE = 1000;
    private static final int MAX_BATCHES_PER_RUN = 20;
    private static final long DAY_MILLIS = 86_400_000L;
    private static final long HOUR_MILLIS = 3_600_000L;

    private final PhoneOneTapAttemptMapper attemptMapper;
    private final PhoneOneTapRateBucketMapper rateBucketMapper;
    private final int attemptRetentionDays;
    private final int rateBucketRetentionHours;
    private final Clock clock;

    @Autowired
    public PhoneOneTapCleanupJob(
            PhoneOneTapAttemptMapper attemptMapper,
            PhoneOneTapRateBucketMapper rateBucketMapper,
            @Value("${app.phone-one-tap.attempt-retention-days:7}") int attemptRetentionDays,
            @Value("${app.phone-one-tap.rate-bucket-retention-hours:2}") int rateBucketRetentionHours
    ) {
        this(
                attemptMapper,
                rateBucketMapper,
                attemptRetentionDays,
                rateBucketRetentionHours,
                Clock.systemUTC()
        );
    }

    PhoneOneTapCleanupJob(
            PhoneOneTapAttemptMapper attemptMapper,
            PhoneOneTapRateBucketMapper rateBucketMapper,
            int attemptRetentionDays,
            int rateBucketRetentionHours,
            Clock clock
    ) {
        if (attemptRetentionDays < 1 || attemptRetentionDays > 365) {
            throw new IllegalArgumentException("一键登录尝试保留天数配置不正确");
        }
        if (rateBucketRetentionHours < 2 || rateBucketRetentionHours > 168) {
            throw new IllegalArgumentException("一键登录限流桶保留小时配置不正确");
        }
        this.attemptMapper = attemptMapper;
        this.rateBucketMapper = rateBucketMapper;
        this.attemptRetentionDays = attemptRetentionDays;
        this.rateBucketRetentionHours = rateBucketRetentionHours;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.phone-one-tap.cleanup-cron:0 35 3 * * ?}")
    public void cleanup() {
        long now = clock.millis();
        deleteAttempts(new Date(now - attemptRetentionDays * DAY_MILLIS));
        deleteRateBuckets(new Date(now - rateBucketRetentionHours * HOUR_MILLIS));
    }

    private void deleteAttempts(Date cutoff) {
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            if (attemptMapper.deleteExpiredAttempts(cutoff, DELETE_BATCH_SIZE) < DELETE_BATCH_SIZE) {
                return;
            }
        }
    }

    private void deleteRateBuckets(Date cutoff) {
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            if (rateBucketMapper.deleteBefore(cutoff, DELETE_BATCH_SIZE) < DELETE_BATCH_SIZE) {
                return;
            }
        }
    }
}
