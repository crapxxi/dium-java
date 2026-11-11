package com.dium.demo.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@ConfigurationPropertiesScan("com.dium.demo.config")
public class TimeConfig {
    /** Часы вынесены в бин, чтобы в тестах можно было «перематывать» время (срок жизни OTP, cooldown). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
