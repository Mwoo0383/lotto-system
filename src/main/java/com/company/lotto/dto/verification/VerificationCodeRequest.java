package com.company.lotto.dto.verification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationCodeRequest {
    private Long verificationId;
    private String code;
}
