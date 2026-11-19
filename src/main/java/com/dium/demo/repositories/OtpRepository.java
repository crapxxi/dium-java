package com.dium.demo.repositories;

import com.dium.demo.enums.OtpPurpose;
import com.dium.demo.models.OtpCode;

import java.util.Optional;
import java.util.OptionalInt;

public interface OtpRepository {
    void save(OtpCode otpCode);
    Optional<OtpCode> find(String phone, OtpPurpose purpose);
    void delete(String phone, OtpPurpose purpose);

    /**
     * Атомарно увеличивает счётчик попыток ввода кода.
     *
     * @return новое значение счётчика или пусто, если кода уже нет (истёк или удалён)
     */
    OptionalInt incrementAttempts(String phone, OtpPurpose purpose);
}
