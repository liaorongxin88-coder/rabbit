package com.rabbit.app.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfiguration {
    @Configuration(proxyBeanMethods = false)
    @Conditional(CacheDisabledCondition.class)
    static class DisabledCacheConfiguration {
        @Bean
        ApplicationCache applicationCache() {
            return new NoopApplicationCache();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(CacheEnabledCondition.class)
    static class RedisCompatibleCacheConfiguration {
        @Bean(destroyMethod = "close")
        CacheBackend cacheBackend(CacheProperties properties) {
            return new LettuceCacheBackend(properties);
        }

        @Bean
        ApplicationCache applicationCache(
                CacheBackend backend,
                CacheProperties properties,
                ObjectMapper objectMapper
        ) {
            return new JsonApplicationCache(
                    backend,
                    objectMapper,
                    properties.getProvider(),
                    properties.getKeyPrefix()
            );
        }
    }

    public static final class CacheEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String provider = provider(context);
            return "redis".equals(provider) || "valkey".equals(provider);
        }
    }

    public static final class CacheDisabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return "none".equals(provider(context));
        }
    }

    private static String provider(ConditionContext context) {
        return context.getEnvironment()
                .getProperty("app.cache.provider", "none")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
