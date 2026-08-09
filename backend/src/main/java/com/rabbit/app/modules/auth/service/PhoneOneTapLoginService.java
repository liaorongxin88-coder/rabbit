package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.support.PhoneNumbers;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PhoneOneTapLoginService {
    private static final Logger log = LoggerFactory.getLogger(PhoneOneTapLoginService.class);
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("aliyun");

    private final List<PhoneOneTapProvider> providers;
    private final PhoneOneTapAttemptService attemptService;
    private final boolean enabled;
    private final Set<String> allowedProviders;
    private final String tokenHashSecret;
    private final int processingLeaseSeconds;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public PhoneOneTapLoginService(
            List<PhoneOneTapProvider> providers,
            PhoneOneTapAttemptService attemptService,
            @Value("${app.phone-one-tap.enabled:false}") boolean enabled,
            @Value("${app.phone-one-tap.allowed-providers:aliyun}") String allowedProviders,
            @Value("${app.phone-one-tap.token-hash-secret:}") String tokenHashSecret,
            @Value("${app.phone-one-tap.processing-lease-seconds:15}") int processingLeaseSeconds,
            @Value("${app.phone-one-tap.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.phone-one-tap.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        this.providers = List.copyOf(providers);
        this.attemptService = attemptService;
        this.enabled = enabled;
        this.allowedProviders = parseAllowedProviders(allowedProviders);
        this.tokenHashSecret = tokenHashSecret;
        this.processingLeaseSeconds = processingLeaseSeconds;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        validateConfiguration();
    }

    public AuthTokenResponse login(String rawProvider, String rawAccessToken, String rawRequestId, String requestIp) {
        try {
            requireEnabled();
            String providerId = normalizeProvider(rawProvider);
            String accessToken = normalizeAccessToken(rawAccessToken);
            String requestId = normalizeRequestId(rawRequestId);
            PhoneOneTapProvider provider = selectProvider(providerId);
            String tokenHash = hashToken(providerId, accessToken);

            PhoneOneTapAttemptService.BeginResult begin = attemptService.begin(
                    requestId,
                    providerId,
                    tokenHash,
                    normalizeRequestIp(requestIp)
            );
            if (begin.getState() == PhoneOneTapAttemptService.BeginResult.State.SUCCEEDED) {
                return attemptService.retrySucceeded(begin.getUserId());
            }
            if (begin.getState() == PhoneOneTapAttemptService.BeginResult.State.FAILED) {
                throw new BizException(begin.getResponseCode(), begin.getResponseMessage());
            }

            String phone;
            try {
                phone = PhoneNumbers.normalizeMainlandMobile(provider.resolvePhone(accessToken, requestId));
            } catch (PhoneOneTapProviderException e) {
                Failure failure = providerFailure(e.getReason());
                attemptService.fail(begin.getAttemptId(), begin.getLeaseId(), failure.code, failure.message);
                throw new BizException(failure.code, failure.message);
            } catch (Exception e) {
                log.error("One-tap login provider call failed: {}", e.getClass().getSimpleName());
                Failure failure = new Failure(502, "一键登录服务暂不可用，请稍后重试");
                attemptService.fail(begin.getAttemptId(), begin.getLeaseId(), failure.code, failure.message);
                throw new BizException(failure.code, failure.message);
            }

            try {
                return attemptService.complete(begin.getAttemptId(), begin.getLeaseId(), phone);
            } catch (PhoneOneTapLeaseLostException e) {
                throw e;
            } catch (BizException e) {
                Failure failure = accountFailure(e);
                attemptService.fail(begin.getAttemptId(), begin.getLeaseId(), failure.code, failure.message);
                throw new BizException(failure.code, failure.message);
            } catch (Exception e) {
                log.error("One-tap login account completion failed: {}", e.getClass().getSimpleName());
                Failure failure = new Failure(500, "一键登录失败，请稍后重试");
                attemptService.fail(begin.getAttemptId(), begin.getLeaseId(), failure.code, failure.message);
                throw new BizException(failure.code, failure.message);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("One-tap login failed: {}", e.getClass().getSimpleName());
            throw new BizException(500, "一键登录失败，请稍后重试");
        }
    }

    private PhoneOneTapProvider selectProvider(String providerId) {
        if (!allowedProviders.contains(providerId)) {
            throw new BizException(400, "不支持的一键登录服务商");
        }
        List<PhoneOneTapProvider> matches = providers.stream()
                .filter(provider -> providerId.equals(normalizeProviderId(provider.providerId())))
                .toList();
        if (matches.size() != 1) {
            throw new BizException(503, "一键登录服务配置不完整");
        }
        return matches.get(0);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BizException(503, "一键登录暂未启用");
        }
    }

    private void validateConfiguration() {
        if (!enabled) {
            return;
        }
        if (tokenHashSecret == null || tokenHashSecret.isBlank() || tokenHashSecret.length() < 32) {
            throw new IllegalArgumentException("一键登录凭证摘要密钥至少需要32个字符");
        }
        if (allowedProviders.isEmpty() || !SUPPORTED_PROVIDERS.containsAll(allowedProviders)) {
            throw new IllegalArgumentException("一键登录服务商白名单配置不正确");
        }
        long leaseMillis = processingLeaseSeconds * 1000L;
        long minimumLeaseMillis = (long) connectTimeoutMs + readTimeoutMs + 1000L;
        if (leaseMillis <= minimumLeaseMillis) {
            throw new IllegalArgumentException("一键登录处理租约必须大于供应商总超时加1000毫秒");
        }
    }

    private String normalizeProvider(String value) {
        String normalized = normalizeProviderId(value);
        if (normalized.isEmpty() || normalized.length() > 32
                || !normalized.matches("[a-z0-9_-]+")) {
            throw new BizException(400, "provider不合法");
        }
        return normalized;
    }

    private String normalizeProviderId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAccessToken(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 4096) {
            throw new BizException(400, "accessToken不合法");
        }
        return normalized;
    }

    private String normalizeRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new BizException(400, "requestId不合法");
        }
        return normalized;
    }

    private String normalizeRequestIp(String requestIp) {
        String normalized = requestIp == null ? "unknown" : requestIp.trim();
        if (normalized.isEmpty()) {
            normalized = "unknown";
        }
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private String hashToken(String provider, String accessToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenHashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (provider + "\0" + accessToken).getBytes(StandardCharsets.UTF_8)
            ));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成一键登录凭证摘要", e);
        }
    }

    private Failure providerFailure(PhoneOneTapProviderException.Reason reason) {
        if (reason == PhoneOneTapProviderException.Reason.DISABLED
                || reason == PhoneOneTapProviderException.Reason.MISCONFIGURED) {
            return new Failure(503, "一键登录服务配置不完整");
        }
        if (reason == PhoneOneTapProviderException.Reason.REJECTED) {
            return new Failure(401, "一键登录凭证无效或已过期");
        }
        return new Failure(502, "一键登录服务暂不可用，请稍后重试");
    }

    private Failure accountFailure(BizException error) {
        if (error.getCode() == 403 && "账号已停用".equals(error.getMessage())) {
            return new Failure(403, "账号已停用");
        }
        return new Failure(500, "一键登录失败，请稍后重试");
    }

    private Set<String> parseAllowedProviders(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(this::normalizeProviderId)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static final class Failure {
        private final int code;
        private final String message;

        private Failure(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
