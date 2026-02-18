package com.company.lotto.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SmsLog {

    private Long smsId;
    private Long participantId;
    private SmsType type;
    private SmsStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime verifiedAt;

    public enum SmsType {
        TICKET,     // 티켓
        REMINDER    // 리마인더
    }

    public enum SmsStatus {
        REQUESTED,  // 요청
        SUCCESS,    // 성공
        FAILED      // 실패
    }
}
