package com.dium.demo.repositories;

import com.dium.demo.models.OtpCode;

import java.util.Optional;

public interface OtpRepository {
    void save(OtpCode otpCode);
    Optional<OtpCode> findById(String phone);
    void delete(OtpCode otpCode);
}
