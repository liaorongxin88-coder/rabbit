package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.entity.PhoneOneTapAttempt;
import com.rabbit.app.modules.auth.mapper.PhoneOneTapAttemptMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PhoneOneTapAttemptService {
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int BEGIN_TRANSACTION_ATTEMPTS = 3;

    private final PhoneOneTapAttemptMapper mapper;
    private final PhoneOneTapRateLimitService rateLimitService;
    private final AuthService authService;
    private final long successRetryWindowMillis;
    private final long processingLeaseMillis;
    private final Clock clock;
    private final Supplier<String> leaseIdSupplier;
    private final TransactionTemplate beginTransaction;

    @Autowired
    public PhoneOneTapAttemptService(
            PhoneOneTapAttemptMapper mapper,
            PhoneOneTapRateLimitService rateLimitService,
            AuthService authService,
            @Value("${app.phone-one-tap.success-retry-window-seconds:30}") int successRetryWindowSeconds,
            @Value("${app.phone-one-tap.processing-lease-seconds:15}") int processingLeaseSeconds,
            PlatformTransactionManager transactionManager
    ) {
        this(
                mapper,
                rateLimitService,
                authService,
                successRetryWindowSeconds,
                processingLeaseSeconds,
                Clock.systemUTC(),
                () -> UUID.randomUUID().toString(),
                transactionTemplate(transactionManager)
        );
    }

    PhoneOneTapAttemptService(
            PhoneOneTapAttemptMapper mapper,
            PhoneOneTapRateLimitService rateLimitService,
            AuthService authService,
            int successRetryWindowSeconds,
            int processingLeaseSeconds,
            Clock clock,
            Supplier<String> leaseIdSupplier,
            TransactionTemplate beginTransaction
    ) {
        if (successRetryWindowSeconds <= 0 || successRetryWindowSeconds > 300) {
            throw new IllegalArgumentException("一键登录成功重试窗口配置不正确");
        }
        if (processingLeaseSeconds <= 0 || processingLeaseSeconds > 300) {
            throw new IllegalArgumentException("一键登录处理租约配置不正确");
        }
        this.mapper = mapper;
        this.rateLimitService = rateLimitService;
        this.authService = authService;
        this.successRetryWindowMillis = successRetryWindowSeconds * 1000L;
        this.processingLeaseMillis = processingLeaseSeconds * 1000L;
        this.clock = clock;
        this.leaseIdSupplier = leaseIdSupplier;
        this.beginTransaction = beginTransaction;
    }

    public BeginResult begin(String requestId, String provider, String tokenHash, String requestIp) {
        rateLimitService.reserve(requestIp);
        for (int attempt = 1; attempt <= BEGIN_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                BeginResult result = beginTransaction.execute(status -> beginState(
                        requestId,
                        provider,
                        tokenHash,
                        requestIp
                ));
                if (result == null) {
                    throw new BizException(500, "一键登录请求状态保存失败");
                }
                return result;
            } catch (ConcurrencyFailureException concurrencyFailure) {
                if (attempt == BEGIN_TRANSACTION_ATTEMPTS) {
                    throw new BizException(503, "一键登录请求繁忙，请稍后重试");
                }
            }
        }
        throw new BizException(503, "一键登录请求繁忙，请稍后重试");
    }

    private BeginResult beginState(
            String requestId,
            String provider,
            String tokenHash,
            String requestIp
    ) {
        PhoneOneTapAttempt existingRequest = mapper.selectByRequestIdForUpdate(requestId);
        if (existingRequest != null) {
            return fromExisting(existingRequest, provider, tokenHash, now());
        }

        PhoneOneTapAttempt existingToken = mapper.selectByTokenHashForUpdate(tokenHash);
        if (existingToken != null) {
            throw new BizException(409, "一键登录凭证无效或已过期");
        }

        PhoneOneTapAttempt attempt = new PhoneOneTapAttempt();
        attempt.setRequestId(requestId);
        attempt.setProvider(provider);
        attempt.setTokenHash(tokenHash);
        attempt.setRequestIp(requestIp);
        attempt.setStatus(STATUS_PROCESSING);
        Date now = now();
        Lease lease = newLease(now);
        attempt.setLeaseId(lease.id);
        attempt.setLeaseExpiresTime(lease.expiresTime);
        try {
            mapper.insert(attempt);
            return BeginResult.created(attempt.getId(), lease.id);
        } catch (DuplicateKeyException duplicate) {
            PhoneOneTapAttempt racedRequest = mapper.selectByRequestIdForUpdate(requestId);
            if (racedRequest != null) {
                return fromExisting(racedRequest, provider, tokenHash, now());
            }
            if (mapper.selectByTokenHashForUpdate(tokenHash) != null) {
                throw new BizException(409, "一键登录凭证无效或已过期");
            }
            throw new BizException(500, "一键登录请求状态保存失败");
        }
    }

    private static TransactionTemplate transactionTemplate(
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }

    @Transactional
    public AuthTokenResponse complete(Long attemptId, String leaseId, String phone) {
        requireOwnedLease(attemptId, leaseId);
        AuthTokenResponse response = authService.loginOrRegisterPhone(phone);
        if (mapper.markSucceeded(attemptId, leaseId, response.getUserId(), now()) != 1) {
            throw new BizException(500, "一键登录请求状态保存失败");
        }
        return response;
    }

    @Transactional
    public void fail(Long attemptId, String leaseId, int responseCode, String responseMessage) {
        requireOwnedLease(attemptId, leaseId);
        if (mapper.markFailed(attemptId, leaseId, responseCode, responseMessage) != 1) {
            throw new BizException(500, "一键登录请求状态保存失败");
        }
    }

    public AuthTokenResponse retrySucceeded(Long userId) {
        if (userId == null) {
            throw new BizException(500, "一键登录请求状态异常");
        }
        return authService.refreshToken(userId);
    }

    private BeginResult fromExisting(
            PhoneOneTapAttempt attempt,
            String provider,
            String tokenHash,
            Date now
    ) {
        if (!provider.equals(attempt.getProvider()) || !constantTimeEquals(tokenHash, attempt.getTokenHash())) {
            throw new BizException(409, "requestId已用于其他一键登录请求");
        }
        if (STATUS_SUCCEEDED.equals(attempt.getStatus())) {
            if (attempt.getSuccessTime() == null
                    || now.getTime() > attempt.getSuccessTime().getTime() + successRetryWindowMillis) {
                throw new BizException(401, "一键登录凭证无效或已过期");
            }
            return BeginResult.succeeded(attempt.getUserId());
        }
        if (STATUS_FAILED.equals(attempt.getStatus())) {
            int code = attempt.getResponseCode() == null ? 502 : attempt.getResponseCode();
            String message = attempt.getResponseMessage() == null
                    ? "一键登录失败，请稍后重试" : attempt.getResponseMessage();
            return BeginResult.failed(code, message);
        }
        if (STATUS_PROCESSING.equals(attempt.getStatus())) {
            if (attempt.getLeaseExpiresTime() != null && attempt.getLeaseExpiresTime().after(now)) {
                throw new BizException(409, "一键登录请求处理中");
            }
            Lease lease = newLease(now);
            if (mapper.replaceLease(
                    attempt.getId(),
                    attempt.getLeaseId(),
                    lease.id,
                    lease.expiresTime
            ) != 1) {
                throw new BizException(409, "一键登录请求处理中");
            }
            return BeginResult.created(attempt.getId(), lease.id);
        }
        throw new BizException(500, "一键登录请求状态异常");
    }

    private void requireOwnedLease(Long attemptId, String leaseId) {
        if (mapper.selectOwnedProcessingForUpdate(attemptId, leaseId, now()) == null) {
            throw new PhoneOneTapLeaseLostException();
        }
    }

    private Lease newLease(Date now) {
        return new Lease(
                leaseIdSupplier.get(),
                new Date(now.getTime() + processingLeaseMillis)
        );
    }

    private Date now() {
        return Date.from(clock.instant());
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII)
        );
    }

    public static final class BeginResult {
        public enum State {
            CREATED,
            SUCCEEDED,
            FAILED
        }

        private final State state;
        private final Long attemptId;
        private final String leaseId;
        private final Long userId;
        private final Integer responseCode;
        private final String responseMessage;

        private BeginResult(
                State state,
                Long attemptId,
                String leaseId,
                Long userId,
                Integer responseCode,
                String responseMessage
        ) {
            this.state = state;
            this.attemptId = attemptId;
            this.leaseId = leaseId;
            this.userId = userId;
            this.responseCode = responseCode;
            this.responseMessage = responseMessage;
        }

        public static BeginResult created(Long attemptId, String leaseId) {
            return new BeginResult(State.CREATED, attemptId, leaseId, null, null, null);
        }

        public static BeginResult succeeded(Long userId) {
            return new BeginResult(State.SUCCEEDED, null, null, userId, null, null);
        }

        public static BeginResult failed(int responseCode, String responseMessage) {
            return new BeginResult(State.FAILED, null, null, null, responseCode, responseMessage);
        }

        public State getState() {
            return state;
        }

        public Long getAttemptId() {
            return attemptId;
        }

        public String getLeaseId() {
            return leaseId;
        }

        public Long getUserId() {
            return userId;
        }

        public Integer getResponseCode() {
            return responseCode;
        }

        public String getResponseMessage() {
            return responseMessage;
        }
    }

    private record Lease(String id, Date expiresTime) {
    }

}
