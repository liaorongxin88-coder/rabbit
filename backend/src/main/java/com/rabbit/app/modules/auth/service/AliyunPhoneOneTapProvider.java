package com.rabbit.app.modules.auth.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.GetMobileRequest;
import com.aliyun.dypnsapi20170525.models.GetMobileResponse;
import com.aliyun.dypnsapi20170525.models.GetMobileResponseBody;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AliyunPhoneOneTapProvider implements PhoneOneTapProvider {
    private static final Logger log = LoggerFactory.getLogger(AliyunPhoneOneTapProvider.class);
    private static final Set<String> REJECTED_CODES = Set.of(
            "ISV.TOKEN_INVALID",
            "ISV.TOKEN_UNAUTHORIZED_USED",
            "ISV.CSRF_CHECK_FAILED",
            "ISV.ACCESS_CODE_ILLEGAL"
    );
    private static final Set<String> MISCONFIGURED_CODES = Set.of(
            "ISV.VERIFY_SCHEME_NOT_EXIST",
            "ISV.VERIFY_SCHEME_CONFLICT",
            "ISV.PACK_SIGN_CONFLICT",
            "ISV.BUNDLE_ID_CONFLICT",
            "ISV.RAM_PERMISSION_DENY",
            "ISP.RAM_PERMISSION_DENY",
            "ISV.SCENE_QUERY_FAIL",
            "ENTITYNOTEXIST.SCENECODE",
            "QUERYFAIL.SCENECODE",
            "ISV.INVALID_APP",
            "ISV.INVALID_PARAMETERS",
            "ISV.PRODUCT_UNSUBSCRIBE",
            "ISV.PRODUCT_UN_SUBSCRIPT",
            "ISV.FORBIDDEN_ACTION",
            "ISV.OUT_OF_SERVICE",
            "ISV.ACCOUNT_NOT_EXISTS",
            "ISV.ACCOUNT_ABNORMAL",
            "ISP.RES_OWNER_ID_UNKNOWN",
            "INVALIDPARAMETER.MISSINGCUSTOMERID",
            "INVALIDPARAMETER.MISSINGPACKAGENAME",
            "ISP.OPERATOR_LIMIT"
    );

    private final boolean enabled;
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final Supplier<Client> testClientSupplier;
    private volatile Client client;

    @Autowired
    public AliyunPhoneOneTapProvider(
            @Value("${app.phone-one-tap.enabled:false}") boolean enabled,
            @Value("${app.phone-one-tap.aliyun.endpoint:dypnsapi.aliyuncs.com}") String endpoint,
            @Value("${app.phone-one-tap.aliyun.access-key-id:}") String accessKeyId,
            @Value("${app.phone-one-tap.aliyun.access-key-secret:}") String accessKeySecret,
            @Value("${app.phone-one-tap.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.phone-one-tap.read-timeout-ms:3000}") int readTimeoutMs
    ) {
        this(enabled, endpoint, accessKeyId, accessKeySecret, connectTimeoutMs, readTimeoutMs, null);
    }

    AliyunPhoneOneTapProvider(
            boolean enabled,
            String endpoint,
            String accessKeyId,
            String accessKeySecret,
            int connectTimeoutMs,
            int readTimeoutMs,
            Supplier<Client> testClientSupplier
    ) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.testClientSupplier = testClientSupplier;
        validateConfiguration();
    }

    @Override
    public String providerId() {
        return "aliyun";
    }

    @Override
    public String resolvePhone(String accessToken, String requestId) {
        if (!enabled) {
            throw new PhoneOneTapProviderException(PhoneOneTapProviderException.Reason.DISABLED);
        }
        GetMobileRequest request = new GetMobileRequest()
                .setAccessToken(accessToken)
                .setOutId(requestId);
        try {
            RuntimeOptions runtime = new RuntimeOptions()
                    .setAutoretry(false)
                    .setMaxAttempts(1)
                    .setConnectTimeout(connectTimeoutMs)
                    .setReadTimeout(readTimeoutMs);
            GetMobileResponse response = client().getMobileWithOptions(request, runtime);
            GetMobileResponseBody body = response == null ? null : response.getBody();
            if (body == null || !Objects.equals("OK", body.getCode())) {
                PhoneOneTapProviderException.Reason reason = classifyCode(
                        body == null ? null : body.getCode()
                );
                log.warn(
                        "Aliyun one-tap login rejected: code={}, requestId={}",
                        safeLogValue(body == null ? null : body.getCode()),
                        safeLogValue(body == null ? null : body.getRequestId())
                );
                throw new PhoneOneTapProviderException(reason);
            }
            if (body.getGetMobileResultDTO() == null
                    || isBlank(body.getGetMobileResultDTO().getMobile())) {
                log.warn("Aliyun one-tap login returned an incomplete response");
                throw new PhoneOneTapProviderException(PhoneOneTapProviderException.Reason.UNAVAILABLE);
            }
            return body.getGetMobileResultDTO().getMobile();
        } catch (PhoneOneTapProviderException e) {
            throw e;
        } catch (TeaException e) {
            PhoneOneTapProviderException.Reason reason = classifyCode(e.getCode());
            log.error("Aliyun one-tap login SDK error: code={}", safeLogValue(e.getCode()));
            throw new PhoneOneTapProviderException(reason);
        } catch (Exception e) {
            log.error("Aliyun one-tap login request failed: {}", e.getClass().getSimpleName());
            throw new PhoneOneTapProviderException(PhoneOneTapProviderException.Reason.UNAVAILABLE);
        }
    }

    PhoneOneTapProviderException.Reason classifyCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (REJECTED_CODES.contains(normalized)) {
            return PhoneOneTapProviderException.Reason.REJECTED;
        }
        if (MISCONFIGURED_CODES.contains(normalized)
                || normalized.startsWith("INVALIDACCESSKEYID")
                || normalized.startsWith("SIGNATUREDOESNOTMATCH")) {
            return PhoneOneTapProviderException.Reason.MISCONFIGURED;
        }
        return PhoneOneTapProviderException.Reason.UNAVAILABLE;
    }

    private Client client() throws Exception {
        if (testClientSupplier != null) {
            return testClientSupplier.get();
        }
        Client current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                Config config = new Config()
                        .setAccessKeyId(accessKeyId)
                        .setAccessKeySecret(accessKeySecret)
                        .setEndpoint(endpoint);
                client = new Client(config);
            }
            return client;
        }
    }

    private void validateConfiguration() {
        if (!enabled) {
            return;
        }
        if (isBlank(endpoint) || isBlank(accessKeyId) || isBlank(accessKeySecret)) {
            throw new IllegalArgumentException("一键登录阿里云配置不完整");
        }
        if (connectTimeoutMs <= 0 || connectTimeoutMs > 30_000
                || readTimeoutMs <= 0 || readTimeoutMs > 30_000) {
            throw new IllegalArgumentException("一键登录阿里云超时配置不正确");
        }
    }

    private String safeLogValue(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,64}")) {
            return "unavailable";
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
