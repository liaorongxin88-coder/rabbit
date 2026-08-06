package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.SmsCodeSendResponse;
import com.rabbit.app.modules.auth.entity.SmsVerificationCode;
import com.rabbit.app.modules.auth.mapper.SmsVerificationCodeMapper;
import com.rabbit.app.modules.auth.support.PhoneNumbers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Date;
import java.util.HexFormat;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmsVerificationService {
    public static final String PURPOSE_LOGIN_OR_REGISTER = "LOGIN_OR_REGISTER";
    private static final String STATUS_PENDING = "PENDING";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SmsVerificationCodeMapper mapper;
    private final SmsSender sender;
    private final PhoneIdentityService phoneIdentityService;
    private final String codeSecret;
    private final int codeLength;
    private final int ttlSeconds;
    private final int resendSeconds;
    private final int maxAttempts;
    private final int phoneHourLimit;
    private final int phoneDayLimit;
    private final int ipHourLimit;
    private final Clock clock;
    private final Supplier<String> codeGenerator;

    @Autowired
    public SmsVerificationService(
            SmsVerificationCodeMapper mapper,
            SmsSender sender,
            PhoneIdentityService phoneIdentityService,
            @Value("${app.sms.code-secret:rabbit-sms-dev-secret-change-me}") String codeSecret,
            @Value("${app.sms.verification.code-length:6}") int codeLength,
            @Value("${app.sms.verification.ttl-seconds:300}") int ttlSeconds,
            @Value("${app.sms.verification.resend-seconds:60}") int resendSeconds,
            @Value("${app.sms.verification.max-attempts:5}") int maxAttempts,
            @Value("${app.sms.verification.phone-hour-limit:5}") int phoneHourLimit,
            @Value("${app.sms.verification.phone-day-limit:10}") int phoneDayLimit,
            @Value("${app.sms.verification.ip-hour-limit:20}") int ipHourLimit
    ) {
        this(
                mapper,
                sender,
                phoneIdentityService,
                codeSecret,
                codeLength,
                ttlSeconds,
                resendSeconds,
                maxAttempts,
                phoneHourLimit,
                phoneDayLimit,
                ipHourLimit,
                Clock.systemUTC(),
                () -> randomCode(codeLength)
        );
    }

    SmsVerificationService(
            SmsVerificationCodeMapper mapper,
            SmsSender sender,
            PhoneIdentityService phoneIdentityService,
            String codeSecret,
            int codeLength,
            int ttlSeconds,
            int resendSeconds,
            int maxAttempts,
            int phoneHourLimit,
            int phoneDayLimit,
            int ipHourLimit,
            Clock clock,
            Supplier<String> codeGenerator
    ) {
        this.mapper = mapper;
        this.sender = sender;
        this.phoneIdentityService = phoneIdentityService;
        this.codeSecret = codeSecret;
        this.codeLength = codeLength;
        this.ttlSeconds = ttlSeconds;
        this.resendSeconds = resendSeconds;
        this.maxAttempts = maxAttempts;
        this.phoneHourLimit = phoneHourLimit;
        this.phoneDayLimit = phoneDayLimit;
        this.ipHourLimit = ipHourLimit;
        this.clock = clock;
        this.codeGenerator = codeGenerator;
        validateSettings();
    }

    public SmsCodeSendResponse sendCode(String rawPhone, String requestIp) {
        String phone = PhoneNumbers.normalizeMainlandMobile(rawPhone);
        String phoneHash = phoneIdentityService.hash(phone);
        String normalizedIp = normalizeRequestIp(requestIp);
        long nowMillis = clock.millis();
        enforceRateLimits(phoneHash, normalizedIp, nowMillis);

        String code = codeGenerator.get();
        if (code == null || !code.matches("\\d{" + codeLength + "}")) {
            throw new IllegalStateException("验证码生成器返回了无效结果");
        }
        SmsVerificationCode item = new SmsVerificationCode();
        item.setPhoneHash(phoneHash);
        item.setPurpose(PURPOSE_LOGIN_OR_REGISTER);
        item.setCodeHash(hash(phone, code));
        item.setRequestIp(normalizedIp);
        item.setStatus(STATUS_PENDING);
        item.setAttemptCount(0);
        item.setSendBucket(nowMillis / (resendSeconds * 1000L));
        item.setExpiresTime(new Date(nowMillis + ttlSeconds * 1000L));

        try {
            mapper.insert(item);
        } catch (DuplicateKeyException e) {
            throw new BizException(429, "验证码发送过于频繁，请稍后重试");
        }

        try {
            sender.sendVerificationCode(phone, code);
            if (mapper.markSent(item.getId()) != 1) {
                throw new BizException(500, "验证码状态保存失败，请重新获取");
            }
        } catch (BizException e) {
            mapper.markFailed(item.getId());
            throw e;
        } catch (Exception e) {
            mapper.markFailed(item.getId());
            throw new BizException(502, "验证码发送失败，请稍后重试");
        }
        return new SmsCodeSendResponse(ttlSeconds, resendSeconds);
    }

    @Transactional(noRollbackFor = BizException.class)
    public String verifyCode(String rawPhone, String code) {
        String phone = PhoneNumbers.normalizeMainlandMobile(rawPhone);
        String phoneHash = phoneIdentityService.hash(phone);
        String normalizedCode = code == null ? "" : code.trim();
        if (!normalizedCode.matches("\\d{" + codeLength + "}")) {
            throw new BizException(400, "请输入" + codeLength + "位验证码");
        }

        Date now = new Date(clock.millis());
        SmsVerificationCode item = mapper.selectLatestActiveForUpdate(
                phoneHash,
                PURPOSE_LOGIN_OR_REGISTER,
                now
        );
        if (item == null) {
            throw new BizException(400, "验证码无效或已过期");
        }
        if (!constantTimeEquals(item.getCodeHash(), hash(phone, normalizedCode))) {
            mapper.recordFailedAttempt(item.getId(), maxAttempts);
            throw new BizException(400, "验证码错误");
        }
        if (mapper.markConsumed(item.getId(), now) != 1) {
            throw new BizException(409, "验证码已使用，请重新获取");
        }
        return phone;
    }

    private void enforceRateLimits(String phoneHash, String requestIp, long nowMillis) {
        if (mapper.countRecentByPhone(
                phoneHash,
                PURPOSE_LOGIN_OR_REGISTER,
                new Date(nowMillis - resendSeconds * 1000L)
        ) > 0) {
            throw new BizException(429, "验证码发送过于频繁，请稍后重试");
        }
        if (mapper.countRecentByPhone(
                phoneHash,
                PURPOSE_LOGIN_OR_REGISTER,
                new Date(nowMillis - 3_600_000L)
        ) >= phoneHourLimit) {
            throw new BizException(429, "该手机号获取验证码次数过多，请稍后再试");
        }
        if (mapper.countRecentByPhone(
                phoneHash,
                PURPOSE_LOGIN_OR_REGISTER,
                new Date(nowMillis - 86_400_000L)
        ) >= phoneDayLimit) {
            throw new BizException(429, "该手机号今日获取验证码次数已达上限");
        }
        if (mapper.countRecentByIp(requestIp, new Date(nowMillis - 3_600_000L)) >= ipHourLimit) {
            throw new BizException(429, "当前网络获取验证码次数过多，请稍后再试");
        }
    }

    private String hash(String phone, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (PURPOSE_LOGIN_OR_REGISTER + ":" + phone + ":" + code)
                            .getBytes(StandardCharsets.UTF_8)
            ));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成验证码摘要", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalizeRequestIp(String requestIp) {
        String value = requestIp == null ? "unknown" : requestIp.trim();
        if (value.isEmpty()) {
            value = "unknown";
        }
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private void validateSettings() {
        if (codeSecret == null || codeSecret.length() < 16) {
            throw new IllegalArgumentException("app.sms.code-secret 至少需要16个字符");
        }
        if (codeLength < 4 || codeLength > 8 || ttlSeconds <= 0 || resendSeconds <= 0
                || maxAttempts <= 0 || phoneHourLimit <= 0 || phoneDayLimit < phoneHourLimit
                || ipHourLimit <= 0) {
            throw new IllegalArgumentException("短信验证码参数配置不正确");
        }
    }

    private static String randomCode(int length) {
        int bound = (int) Math.pow(10, length);
        int floor = (int) Math.pow(10, length - 1);
        return Integer.toString(floor + SECURE_RANDOM.nextInt(bound - floor));
    }
}
