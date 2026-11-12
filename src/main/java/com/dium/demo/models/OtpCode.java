package com.dium.demo.models;

import com.dium.demo.enums.OtpPurpose;
import lombok.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OtpCode {
    private String phone;
    private OtpPurpose purpose;
    private String code;
    private Instant createdAt;
    private Instant expiresAt;

    /** Сколько раз код уже пытались проверить. */
    @Builder.Default
    private AtomicInteger attempts = new AtomicInteger();
}
