package com.melita.orders.intake.config;

import com.melita.orders.intake.catalogue.Catalogue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * Cache-aside for the product catalogue. Redis is an optimisation, not a dependency: if it is down or slow,
 * errors are logged and the catalogue is read from MySQL.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String CATALOGUE = "catalogue";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * One typed serializer for the one cache: the JSON never names Java classes, so no polymorphic
     * deserialisation is involved.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer catalogueCache(JsonMapper jsonMapper) {
        var serializer = new JacksonJsonRedisSerializer<>(jsonMapper, Catalogue.class);
        return builder -> builder.withCacheConfiguration(CATALOGUE, RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .prefixCacheNameWith("order-intake:")
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer)));
    }

    /** A release may change the catalogue (Flyway), so drop what the previous version cached. */
    @EventListener(ApplicationReadyEvent.class)
    void evictCatalogueOnStartup(ApplicationReadyEvent event) {
        CacheManager cacheManager = event.getApplicationContext().getBean(CacheManager.class);
        Cache cache = cacheManager.getCache(CATALOGUE);
        if (cache != null) {
            try {
                cache.clear();
            } catch (RuntimeException e) {
                log.warn("Could not clear the catalogue cache on startup: {}", e.getMessage());
            }
        }
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache {} unavailable, reading from the database: {}", cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Could not write to cache {}: {}", cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Could not evict from cache {}: {}", cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Could not clear cache {}: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
