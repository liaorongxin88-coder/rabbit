package com.rabbit.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApplicationSecretValidatorTest {
    private static final String JWT_SECRET = "unit-app-jwt-secret-0123456789abcdef";
    private static final String ADMIN_JWT_SECRET = "unit-admin-jwt-secret-0123456789abcdef";
    private static final String PHONE_HASH_SECRET = "unit-phone-hash-secret-0123456789abcdef";
    private static final String SMS_CODE_SECRET = "unit-sms-code-secret-0123456789abcdef";

    @Test
    void acceptsDistinctConfiguredSecretsWithSmsDisabled() {
        runner(JWT_SECRET, ADMIN_JWT_SECRET, PHONE_HASH_SECRET, false, "")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsMissingAlwaysRequiredSecrets() {
        assertFailure("", ADMIN_JWT_SECRET, PHONE_HASH_SECRET, false, "", "APP_JWT_SECRET");
        assertFailure(JWT_SECRET, "", PHONE_HASH_SECRET, false, "", "APP_ADMIN_JWT_SECRET");
        assertFailure(JWT_SECRET, ADMIN_JWT_SECRET, "", false, "", "APP_PHONE_HASH_SECRET");
    }

    @Test
    void rejectsKnownPlaceholdersAndSharedJwtSecrets() {
        assertFailure(
                "too-short",
                ADMIN_JWT_SECRET,
                PHONE_HASH_SECRET,
                false,
                "",
                "APP_JWT_SECRET"
        );
        assertFailure(
                "rabbit-app-dev-secret-change-me-32bytes",
                ADMIN_JWT_SECRET,
                PHONE_HASH_SECRET,
                false,
                "",
                "APP_JWT_SECRET"
        );
        assertFailure(
                JWT_SECRET,
                JWT_SECRET,
                PHONE_HASH_SECRET,
                false,
                "",
                "APP_ADMIN_JWT_SECRET must be different from APP_JWT_SECRET"
        );
    }

    @Test
    void requiresSmsCodeSecretOnlyWhenSmsIsEnabled() {
        assertFailure(
                JWT_SECRET,
                ADMIN_JWT_SECRET,
                PHONE_HASH_SECRET,
                true,
                "",
                "APP_SMS_CODE_SECRET"
        );
        assertFailure(
                JWT_SECRET,
                ADMIN_JWT_SECRET,
                PHONE_HASH_SECRET,
                true,
                "rabbit-sms-dev-secret-change-me",
                "APP_SMS_CODE_SECRET"
        );
        runner(JWT_SECRET, ADMIN_JWT_SECRET, PHONE_HASH_SECRET, true, SMS_CODE_SECRET)
                .withPropertyValues("app.cache.provider=redis")
                .run(context -> assertThat(context).hasNotFailed());
        runner(JWT_SECRET, ADMIN_JWT_SECRET, PHONE_HASH_SECRET, true, SMS_CODE_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .contains("APP_CACHE_PROVIDER");
                });
    }

    private void assertFailure(
            String jwtSecret,
            String adminJwtSecret,
            String phoneHashSecret,
            boolean smsEnabled,
            String smsCodeSecret,
            String expectedMessage
    ) {
        runner(jwtSecret, adminJwtSecret, phoneHashSecret, smsEnabled, smsCodeSecret)
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable rootCause = context.getStartupFailure();
                    assertThat(rootCause(rootCause).getMessage()).contains(expectedMessage);
                });
    }

    private ApplicationContextRunner runner(
            String jwtSecret,
            String adminJwtSecret,
            String phoneHashSecret,
            boolean smsEnabled,
            String smsCodeSecret
    ) {
        return new ApplicationContextRunner()
                .withBean(ApplicationSecretValidator.class)
                .withPropertyValues(
                        "app.jwt.secret=" + jwtSecret,
                        "app.admin.jwt.secret=" + adminJwtSecret,
                        "app.auth.phone-hash-secret=" + phoneHashSecret,
                        "app.sms.enabled=" + smsEnabled,
                        "app.sms.code-secret=" + smsCodeSecret
                );
    }

    private Throwable rootCause(Throwable failure) {
        Throwable rootCause = failure;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
