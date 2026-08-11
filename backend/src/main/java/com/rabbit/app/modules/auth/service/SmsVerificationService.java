package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.config.ApplicationSecretValidator;
import com.rabbit.app.modules.auth.dto.SmsCodeSendResponse;
import com.rabbit.app.modules.auth.support.PhoneNumbers;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsVerificationService {
    private static final Logger log = LoggerFactory.getLogger(SmsVerificationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SmsVerificationStore store;
    private final SmsSender sender;
    private final PhoneIdentityService phoneIdentityService;
    private final boolean enabled;
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
            SmsVerificationStore store,
            SmsSender sender,
            PhoneIdentityService phoneIdentityService,
            @Value("${app.sms.enabled:false}") boolean enabled,
            @Value("${app.sms.code-secret:}") String codeSecret,
            @Value("${app.sms.verification.code-length:6}") int codeLength,
            @Value("${app.sms.verification.ttl-seconds:300}") int ttlSeconds,
            @Value("${app.sms.verification.resend-seconds:60}") int resendSeconds,
            @Value("${app.sms.verification.max-attempts:5}") int maxAttempts,
            @Value("${app.sms.verification.phone-hour-limit:5}") int phoneHourLimit,
            @Value("${app.sms.verification.phone-day-limit:10}") int phoneDayLimit,
            @Value("${app.sms.verification.ip-hour-limit:20}") int ipHourLimit
    ) {
        this(
                store,
                sender,
                phoneIdentityService,
                enabled,
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
            SmsVerificationStore store,
            SmsSender sender,
            PhoneIdentityService phoneIdentityService,
            boolean enabled,
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
        this.store = store;
        this.sender = sender;
        this.phoneIdentityService = phoneIdentityService;
        this.enabled = enabled;
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
        return sendCode(rawPhone, SmsVerificationPurpose.LOGIN_OR_REGISTER, requestIp);
    }

    public SmsCodeSendResponse sendCode(
            String rawPhone,
            SmsVerificationPurpose purpose,
            String requestIp
    ) {
        requireEnabled();
        String phone = PhoneNumbers.normalizeMainlandMobile(rawPhone);
        String phoneHash = phoneIdentityService.hash(phone);
        String requestIpHash = hashRequestIp(normalizeRequestIp(requestIp));
        long nowMillis = clock.millis();

        String code = codeGenerator.get();
        if (code == null || !code.matches("\\d{" + codeLength + "}")) {
            throw new IllegalStateException("验证码生成器返回了无效结果");
        }
        SmsVerificationStore.Reservation reservation = new SmsVerificationStore.Reservation(
                UUID.randomUUID().toString(),
                phoneHash,
                requestIpHash,
                purpose,
                hashCode(purpose, phone, code),
                nowMillis,
                Duration.ofSeconds(ttlSeconds),
                Duration.ofSeconds(resendSeconds),
                phoneHourLimit,
                phoneDayLimit,
                ipHourLimit
        );
        enforceReserveResult(reserve(reservation));

        try {
            sender.sendVerificationCode(phone, code);
        } catch (BizException e) {
            cancelQuietly(reservation);
            throw e;
        } catch (Exception e) {
            cancelQuietly(reservation);
            throw new BizException(502, "验证码发送失败，请稍后重试");
        }

        SmsVerificationStore.ActivationResult activation = activate(reservation);
        if (activation != SmsVerificationStore.ActivationResult.ACTIVATED) {
            throw new BizException(500, "验证码状态保存失败，请重新获取");
        }
        return new SmsCodeSendResponse(ttlSeconds, resendSeconds);
    }

    public String verifyCode(String rawPhone, String code) {
        return verifyCode(rawPhone, code, SmsVerificationPurpose.LOGIN_OR_REGISTER);
    }

    public String verifyCode(
            String rawPhone,
            String code,
            SmsVerificationPurpose purpose
    ) {
        requireEnabled();
        String phone = PhoneNumbers.normalizeMainlandMobile(rawPhone);
        String phoneHash = phoneIdentityService.hash(phone);
        String normalizedCode = code == null ? "" : code.trim();
        if (!normalizedCode.matches("\\d{" + codeLength + "}")) {
            throw new BizException(400, "请输入" + codeLength + "位验证码");
        }

        SmsVerificationStore.VerificationResult result = verifyAndConsume(
                phoneHash,
                purpose,
                hashCode(purpose, phone, normalizedCode)
        );
        if (result == SmsVerificationStore.VerificationResult.MISSING) {
            throw new BizException(400, "验证码无效或已过期");
        }
        if (result == SmsVerificationStore.VerificationResult.WRONG
                || result == SmsVerificationStore.VerificationResult.LOCKED) {
            throw new BizException(400, "验证码错误");
        }
        return phone;
    }

    private SmsVerificationStore.ReserveResult reserve(SmsVerificationStore.Reservation reservation) {
        try {
            return store.reserve(reservation);
        } catch (SmsVerificationStoreUnavailableException unavailable) {
            throw cacheUnavailable(unavailable);
        }
    }

    private SmsVerificationStore.ActivationResult activate(SmsVerificationStore.Reservation reservation) {
        try {
            return store.activate(reservation);
        } catch (SmsVerificationStoreUnavailableException unavailable) {
            throw cacheUnavailable(unavailable);
        }
    }

    private SmsVerificationStore.VerificationResult verifyAndConsume(
            String phoneHash,
            SmsVerificationPurpose purpose,
            String submittedCodeHash
    ) {
        try {
            return store.verifyAndConsume(phoneHash, purpose, submittedCodeHash, maxAttempts);
        } catch (SmsVerificationStoreUnavailableException unavailable) {
            throw cacheUnavailable(unavailable);
        }
    }

    private void cancelQuietly(SmsVerificationStore.Reservation reservation) {
        try {
            store.cancel(reservation);
        } catch (SmsVerificationStoreUnavailableException unavailable) {
            log.warn("Unable to cancel failed SMS cache reservation: purpose={}, error={}",
                    reservation.purpose(), unavailable.getClass().getSimpleName());
        }
    }

    private void enforceReserveResult(SmsVerificationStore.ReserveResult result) {
        switch (result) {
            case RESERVED -> {
            }
            case RESEND_LIMIT -> throw new BizException(429, "验证码发送过于频繁，请稍后重试");
            case PHONE_HOUR_LIMIT -> throw new BizException(429, "该手机号获取验证码次数过多，请稍后再试");
            case PHONE_DAY_LIMIT -> throw new BizException(429, "该手机号今日获取验证码次数已达上限");
            case IP_HOUR_LIMIT -> throw new BizException(429, "当前网络获取验证码次数过多，请稍后再试");
        }
    }

    private BizException cacheUnavailable(SmsVerificationStoreUnavailableException unavailable) {
        log.warn("SMS verification cache unavailable: error={}", unavailable.getClass().getSimpleName());
        return new BizException(503, "验证码服务暂不可用，请稍后重试");
    }

    private String hashCode(SmsVerificationPurpose purpose, String phone, String code) {
        return hmac(purpose.name() + ":" + phone + ":" + code);
    }

    private String hashRequestIp(String requestIp) {
        return hmac("REQUEST_IP:" + requestIp);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成验证码摘要", e);
        }
    }

    private String normalizeRequestIp(String requestIp) {
        String value = requestIp == null ? "unknown" : requestIp.trim();
        if (value.isEmpty()) {
            value = "unknown";
        }
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private void validateSettings() {
        if (enabled) {
            ApplicationSecretValidator.requireConfigured("APP_SMS_CODE_SECRET", codeSecret);
        }
        if (codeLength < 4 || codeLength > 8 || ttlSeconds <= 0 || resendSeconds <= 0
                || maxAttempts <= 0 || phoneHourLimit <= 0 || phoneDayLimit < phoneHourLimit
                || ipHourLimit <= 0) {
            throw new IllegalArgumentException("短信验证码参数配置不正确");
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BizException(503, "短信登录暂未启用");
        }
    }

    private static String randomCode(int length) {
        int bound = (int) Math.pow(10, length);
        int floor = (int) Math.pow(10, length - 1);
        return Integer.toString(floor + SECURE_RANDOM.nextInt(bound - floor));
    }
}
