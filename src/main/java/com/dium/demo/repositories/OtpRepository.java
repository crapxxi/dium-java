package com.dium.demo.repositories;

import com.dium.demo.models.OtpCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OtpRepository {

    private final CacheManager cacheManager;

    private Cache getCache() {
        return cacheManager.getCache("otpCodes");
    }

    public void save(OtpCode otpCode) {
        getCache().put(otpCode.getPhone(), otpCode);
    }

    public Optional<OtpCode> findById(String phone) {
        OtpCode code = getCache().get(phone, OtpCode.class);
        return Optional.ofNullable(code);
    }

    public void delete(OtpCode otpCode) {
        getCache().evict(otpCode.getPhone());
    }
}