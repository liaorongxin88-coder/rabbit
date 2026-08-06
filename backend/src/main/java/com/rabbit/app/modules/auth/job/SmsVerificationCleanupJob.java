package com.rabbit.app.modules.auth.job;

import com.rabbit.app.modules.auth.mapper.SmsVerificationCodeMapper;
import java.time.Clock;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SmsVerificationCleanupJob {
    private static final int DELETE_BATCH_SIZE = 1000;
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final SmsVerificationCodeMapper mapper;
    private final int retentionDays;
    private final Clock clock = Clock.systemUTC();

    public SmsVerificationCleanupJob(
            SmsVerificationCodeMapper mapper,
            @Value("${app.sms.verification.retention-days:7}") int retentionDays
    ) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("短信验证码保留天数必须大于0");
        }
        this.mapper = mapper;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.sms.verification.cleanup-cron:0 20 3 * * ?}")
    public void cleanup() {
        long retentionMillis = retentionDays * 86_400_000L;
        Date cutoff = new Date(clock.millis() - retentionMillis);
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            if (mapper.deleteCreatedBefore(cutoff, DELETE_BATCH_SIZE) < DELETE_BATCH_SIZE) {
                return;
            }
        }
    }
}
