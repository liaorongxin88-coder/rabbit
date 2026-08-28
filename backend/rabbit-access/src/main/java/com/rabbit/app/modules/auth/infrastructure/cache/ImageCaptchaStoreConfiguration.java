package com.rabbit.app.modules.auth.infrastructure.cache;

import com.rabbit.app.cache.CacheBackend;
import com.rabbit.app.cache.CacheConfiguration;
import com.rabbit.app.cache.CacheProperties;
import com.rabbit.app.modules.auth.service.ImageCaptchaStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ImageCaptchaStoreConfiguration {
    @Configuration(proxyBeanMethods = false)
    @Conditional(CacheConfiguration.CacheDisabledCondition.class)
    static class DisabledImageCaptchaStoreConfiguration {
        @Bean
        ImageCaptchaStore imageCaptchaStore() {
            return new UnavailableImageCaptchaStore();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(CacheConfiguration.CacheEnabledCondition.class)
    static class RedisCompatibleImageCaptchaStoreConfiguration {
        @Bean
        ImageCaptchaStore imageCaptchaStore(CacheBackend backend, CacheProperties properties) {
            return new LettuceImageCaptchaStore(backend, properties.getKeyPrefix());
        }
    }
}
