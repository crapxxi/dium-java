package com.dium.demo.repositories;

import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.models.OtpCode;

import java.util.Optional;

public interface OtpRepository {
    void save(OtpCode otpCode);
    Optional<OtpCode> find(String phone, OtpPurpose purpose);
    void delete(String phone, OtpPurpose purpose);
}
