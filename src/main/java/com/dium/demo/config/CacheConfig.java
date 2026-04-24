package com.dium.demo.config;

import com.dium.demo.models.OtpCode;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.cache.autoconfigure.JCacheManagerCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public JCacheManagerCustomizer cacheManagerCustomizer() {
        return cacheManager -> {

            MutableConfiguration<String, OtpCode> otpConfig = new MutableConfiguration<String, OtpCode>()
                    .setTypes(String.class, OtpCode.class)
                    .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 5)))
                    .setStoreByValue(false);

            MutableConfiguration<String, Object> bucketsConfig = new MutableConfiguration<String, Object>()
                    .setTypes(String.class, Object.class)
                    .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1)))
                    .setStoreByValue(false);

            cacheManager.createCache("otpCodes", otpConfig);
            cacheManager.createCache("buckets", bucketsConfig);
        };
    }
}