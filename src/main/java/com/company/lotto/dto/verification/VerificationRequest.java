package com.company.lotto.dto.verification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationRequest {
    private String phoneNumber;
    private Long eventId;
}
