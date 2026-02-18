package com.company.lotto.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhoneVerification {

    private Long verificationId;
    private Long eventId;
    private Long participantId;
    private VerificationStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime expiredAt;

    public enum VerificationStatus {
        REQUESTED,  // 인증요청
        VERIFIED,   // 인증완료
        EXPIRED     // 인증만료
    }
}
