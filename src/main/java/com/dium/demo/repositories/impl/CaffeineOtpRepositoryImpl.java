package com.dium.demo.repositories.impl;

import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.models.OtpCode;
import com.dium.demo.repositories.OtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CaffeineOtpRepositoryImpl implements OtpRepository {

    private final CacheManager cacheManager;

    private Cache getCache() {
        return cacheManager.getCache("otpCodes");
    }

    /** Ключ = телефон + назначение, чтобы коды разных сценариев не перезаписывали друг друга. */
    private static String key(String phone, OtpPurpose purpose) {
        return phone + ":" + purpose.name();
    }

    @Override
    public void save(OtpCode otpCode) {
        getCache().put(key(otpCode.getPhone(), otpCode.getPurpose()), otpCode);
    }

    @Override
    public Optional<OtpCode> find(String phone, OtpPurpose purpose) {
        OtpCode code = getCache().get(key(phone, purpose), OtpCode.class);
        return Optional.ofNullable(code);
    }

    @Override
    public void delete(String phone, OtpPurpose purpose) {
        getCache().evict(key(phone, purpose));
    }
}
