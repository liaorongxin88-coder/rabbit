package com.rabbit.app.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.modules.auth.service.SmsVerificationStore;
import com.rabbit.app.modules.auth.infrastructure.cache.LettuceSmsVerificationStore;
import com.rabbit.app.modules.auth.infrastructure.cache.SmsVerificationStoreConfiguration;
import com.rabbit.app.modules.auth.infrastructure.cache.UnavailableSmsVerificationStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CacheConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    CacheConfiguration.class,
                    SmsVerificationStoreConfiguration.class,
                    JacksonTestConfiguration.class
            );

    @Test
    void usesNoopCacheByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ApplicationCache.class);
            assertThat(context.getBean(ApplicationCache.class)).isInstanceOf(NoopApplicationCache.class);
            assertThat(context.getBean(SmsVerificationStore.class))
                    .isInstanceOf(UnavailableSmsVerificationStore.class);
            assertThat(context.getBean(CacheProperties.class).getProvider()).isEqualTo(CacheProvider.NONE);
        });
    }

    @Test
    void configuresRedisWithoutConnectingAtStartup() {
        assertProviderCreatesLettuceCache("redis", CacheProvider.REDIS);
    }

    @Test
    void configuresValkeyWithoutConnectingAtStartup() {
        assertProviderCreatesLettuceCache("valkey", CacheProvider.VALKEY);
    }

    @Test
    void rejectsUnknownProvider() {
        contextRunner
                .withPropertyValues("app.cache.provider=memcached")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsInvalidTimeout() {
        contextRunner
                .withPropertyValues(
                        "app.cache.provider=redis",
                        "app.cache.command-timeout=0s"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    private void assertProviderCreatesLettuceCache(String propertyValue, CacheProvider expectedProvider) {
        contextRunner
                .withPropertyValues(
                        "app.cache.provider=" + propertyValue,
                        "app.cache.host=cache.internal"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ApplicationCache.class);
                    assertThat(context.getBean(ApplicationCache.class)).isInstanceOf(JsonApplicationCache.class);
                    assertThat(context.getBean(SmsVerificationStore.class))
                            .isInstanceOf(LettuceSmsVerificationStore.class);
                    CacheProperties properties = context.getBean(CacheProperties.class);
                    assertThat(properties.getProvider()).isEqualTo(expectedProvider);
                    assertThat(properties.getHost()).isEqualTo("cache.internal");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class JacksonTestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
