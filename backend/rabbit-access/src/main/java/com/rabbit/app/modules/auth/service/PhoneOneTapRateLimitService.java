package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.mapper.PhoneOneTapRateBucketMapper;
import java.time.Clock;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneOneTapRateLimitService {
    private static final String BUCKET_MINUTE = "MINUTE";
    private static final String BUCKET_HOUR = "HOUR";
    private static final long MINUTE_MILLIS = 60_000L;
    private static final long HOUR_MILLIS = 3_600_000L;

    private final PhoneOneTapRateBucketMapper mapper;
    private final int ipMinuteLimit;
    private final int ipHourLimit;
    private final Clock clock;

    @Autowired
    public PhoneOneTapRateLimitService(
            PhoneOneTapRateBucketMapper mapper,
            @Value("${app.phone-one-tap.rate-limit.ip-minute-limit:10}") int ipMinuteLimit,
            @Value("${app.phone-one-tap.rate-limit.ip-hour-limit:60}") int ipHourLimit
    ) {
        this(mapper, ipMinuteLimit, ipHourLimit, Clock.systemUTC());
    }

    PhoneOneTapRateLimitService(
            PhoneOneTapRateBucketMapper mapper,
            int ipMinuteLimit,
            int ipHourLimit,
            Clock clock
    ) {
        if (ipMinuteLimit <= 0 || ipHourLimit < ipMinuteLimit) {
            throw new IllegalArgumentException("一键登录IP限流参数配置不正确");
        }
        this.mapper = mapper;
        this.ipMinuteLimit = ipMinuteLimit;
        this.ipHourLimit = ipHourLimit;
        this.clock = clock;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED
    )
    public void reserve(String requestIp) {
        Date now = Date.from(clock.instant());
        Date minuteStart = bucketStart(now, MINUTE_MILLIS);
        Date hourStart = bucketStart(now, HOUR_MILLIS);

        int minuteCount = lockBucket(requestIp, BUCKET_MINUTE, minuteStart);
        int hourCount = lockBucket(requestIp, BUCKET_HOUR, hourStart);
        if (minuteCount >= ipMinuteLimit || hourCount >= ipHourLimit) {
            throw new BizException(429, "当前网络一键登录次数过多，请稍后再试");
        }
        if (mapper.increment(requestIp, BUCKET_MINUTE, minuteStart) != 1
                || mapper.increment(requestIp, BUCKET_HOUR, hourStart) != 1) {
            throw new BizException(500, "一键登录限流状态保存失败");
        }
    }

    private int lockBucket(String requestIp, String bucketType, Date bucketStart) {
        mapper.lockOrCreate(requestIp, bucketType, bucketStart);
        Integer count = mapper.selectCountForUpdate(requestIp, bucketType, bucketStart);
        if (count == null) {
            throw new BizException(500, "一键登录限流状态保存失败");
        }
        return count;
    }

    private Date bucketStart(Date now, long bucketMillis) {
        return new Date((now.getTime() / bucketMillis) * bucketMillis);
    }
}
