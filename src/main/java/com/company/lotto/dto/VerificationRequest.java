package com.company.lotto.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationRequest {
    private String phoneNumber;
    private Long eventId;
}
