package com.dium.demo.models;

import lombok.*;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OtpCode {
    private String phone;
    private String code;

}
