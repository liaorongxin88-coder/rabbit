package com.rabbit.app.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 缓存配置的约束。
 *
 * <p>这些约束是启动期的守门员。配错了要在启动时就炸，而不是等到线上第一次读缓存
 * 才发现——那时表现为「缓存莫名其妙不生效」或者连接一直超时，排查成本高得多。
 *
 * <p>两条跨字段规则值得单独盯：超时为零意味着请求永不重试直接失败，而只填用户名
 * 不填密码会让 Redis 认证以一种含糊的方式失败。
 */
class CachePropertiesTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void theDefaultConfigurationIsValid() {
        assertTrue(violations(new CacheProperties()).isEmpty());
    }

    /**
     * 默认必须是关闭状态：新环境没配缓存时应当安静地不启用，而不是尝试连本机 Redis。
     */
    @Test
    void cachingIsOffUntilExplicitlyConfigured() {
        assertEquals(CacheProvider.NONE, new CacheProperties().getProvider());
    }

    // ---------- 超时 ----------

    @Test
    void aZeroConnectTimeoutIsRejected() {
        CacheProperties properties = new CacheProperties();
        properties.setConnectTimeout(Duration.ZERO);

        assertFalse(properties.isTimeoutConfigurationValid());
        assertTrue(violations(properties).contains("cache timeouts must be positive"));
    }

    @Test
    void aNegativeCommandTimeoutIsRejected() {
        CacheProperties properties = new CacheProperties();
        properties.setCommandTimeout(Duration.ofSeconds(-1));

        assertFalse(properties.isTimeoutConfigurationValid());
    }

    @Test
    void aZeroCommandTimeoutIsRejected() {
        CacheProperties properties = new CacheProperties();
        properties.setCommandTimeout(Duration.ZERO);

        assertFalse(properties.isTimeoutConfigurationValid());
    }

    @Test
    void positiveTimeoutsArAccepted() {
        CacheProperties properties = new CacheProperties();
        properties.setConnectTimeout(Duration.ofMillis(1));
        properties.setCommandTimeout(Duration.ofMillis(1));

        assertTrue(properties.isTimeoutConfigurationValid());
    }

    // ---------- 认证 ----------

    /**
     * 只给用户名不给密码，Redis 侧会以一种不好排查的方式拒绝认证，所以在配置期就拦下。
     */
    @Test
    void aUsernameWithoutAPasswordIsRejected() {
        CacheProperties properties = new CacheProperties();
        properties.setUsername("cache-user");
        properties.setPassword("");

        assertFalse(properties.isAuthenticationConfigurationValid());
        assertTrue(violations(properties).contains("cache username requires a password"));
    }

    @Test
    void aBlankUsernameNeedsNoPassword() {
        CacheProperties properties = new CacheProperties();
        properties.setUsername("   ");
        properties.setPassword(null);

        assertTrue(properties.isAuthenticationConfigurationValid());
    }

    /**
     * 只配密码不配用户名是 Redis 的常见用法（requirepass），不该被拦。
     */
    @Test
    void aPasswordWithoutAUsernameIsAllowed() {
        CacheProperties properties = new CacheProperties();
        properties.setUsername("");
        properties.setPassword("secret");

        assertTrue(properties.isAuthenticationConfigurationValid());
    }

    @Test
    void aUsernameWithAPasswordIsAccepted() {
        CacheProperties properties = new CacheProperties();
        properties.setUsername("cache-user");
        properties.setPassword("secret");

        assertTrue(properties.isAuthenticationConfigurationValid());
        assertTrue(violations(properties).isEmpty());
    }

    // ---------- 端口与主机 ----------

    @Test
    void aPortOutsideTheValidRangeIsRejected() {
        CacheProperties tooLow = new CacheProperties();
        tooLow.setPort(0);
        assertFalse(violations(tooLow).isEmpty());

        CacheProperties tooHigh = new CacheProperties();
        tooHigh.setPort(65536);
        assertFalse(violations(tooHigh).isEmpty());
    }

    @Test
    void aBlankHostIsRejected() {
        CacheProperties properties = new CacheProperties();
        properties.setHost("  ");

        assertFalse(violations(properties).isEmpty());
    }

    @Test
    void aNegativeDatabaseIndexIsRejected() {
        CacheProperties properties = new CacheProperties();
        properties.setDatabase(-1);

        assertFalse(violations(properties).isEmpty());
    }

    // ---------- key 前缀 ----------

    /**
     * 前缀是缓存键的命名空间。允许冒号分段，但不能带空格或通配符 —— 那会让
     * {@code KEYS}/{@code SCAN} 之类的运维操作匹配到计划外的键。
     */
    @Test
    void aKeyPrefixWithSpacesIsRejected() {
        CacheProperties properties = new CacheProperties();
        properties.setKeyPrefix("rabbit cache");

        assertFalse(violations(properties).isEmpty());
    }

    @Test
    void aKeyPrefixWithWildcardsIsRejected() {
        CacheProperties properties = new CacheProperties();
        properties.setKeyPrefix("rabbit:*");

        assertFalse(violations(properties).isEmpty());
    }

    @Test
    void aKeyPrefixMustStartWithAnAlphanumeric() {
        CacheProperties properties = new CacheProperties();
        properties.setKeyPrefix(":leading:colon");

        assertFalse(violations(properties).isEmpty());
    }

    @Test
    void aSegmentedKeyPrefixIsAccepted() {
        CacheProperties properties = new CacheProperties();
        properties.setKeyPrefix("rabbit:cache:v2");

        assertTrue(violations(properties).isEmpty());
    }

    @Test
    void anOverlongKeyPrefixIsRejected() {
        CacheProperties properties = new CacheProperties();
        properties.setKeyPrefix("a".repeat(129));

        assertFalse(violations(properties).isEmpty());
    }

    @Test
    void aKeyPrefixAtTheLengthLimitIsAccepted() {
        CacheProperties properties = new CacheProperties();
        properties.setKeyPrefix("a".repeat(128));

        assertTrue(violations(properties).isEmpty());
    }

    private Set<String> violations(CacheProperties properties) {
        return validator.validate(properties).stream()
                .map(v -> v.getMessage())
                .collect(Collectors.toSet());
    }
}
