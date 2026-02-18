package com.company.lotto.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationCodeRequest {
    private Long verificationId;
    private String code;
}
