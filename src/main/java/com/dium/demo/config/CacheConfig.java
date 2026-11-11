package com.dium.demo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        CaffeineCache otpCache = new CaffeineCache("otpCodes",
                Caffeine.newBuilder()
                        .expireAfterWrite(15, TimeUnit.MINUTES) // с запасом: точный срок жизни кода проверяет OtpService
                        .maximumSize(10000)
                        .build());

        CaffeineCache bucketsCache = new CaffeineCache("buckets",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(5000)
                        .build());

        cacheManager.setCaches(Arrays.asList(otpCache, bucketsCache));
        return cacheManager;
    }
}
