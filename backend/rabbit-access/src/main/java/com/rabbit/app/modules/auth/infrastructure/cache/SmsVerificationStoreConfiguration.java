package com.rabbit.app.modules.auth.infrastructure.cache;

import com.rabbit.app.cache.CacheBackend;
import com.rabbit.app.cache.CacheConfiguration;
import com.rabbit.app.cache.CacheProperties;
import com.rabbit.app.modules.auth.service.SmsVerificationStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SmsVerificationStoreConfiguration {
    @Configuration(proxyBeanMethods = false)
    @Conditional(CacheConfiguration.CacheDisabledCondition.class)
    static class DisabledSmsVerificationStoreConfiguration {
        @Bean
        SmsVerificationStore smsVerificationStore() {
            return new UnavailableSmsVerificationStore();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(CacheConfiguration.CacheEnabledCondition.class)
    static class RedisCompatibleSmsVerificationStoreConfiguration {
        @Bean
        SmsVerificationStore smsVerificationStore(CacheBackend backend, CacheProperties properties) {
            return new LettuceSmsVerificationStore(backend, properties.getKeyPrefix());
        }
    }
}
