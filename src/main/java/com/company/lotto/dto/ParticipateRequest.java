package com.company.lotto.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticipateRequest {
    private String phoneNumber;
    private Long eventId;
    private Long verificationId;
}
