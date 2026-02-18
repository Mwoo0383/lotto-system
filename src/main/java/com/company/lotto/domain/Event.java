package com.company.lotto.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Event {

    private Long eventId;
    private String name;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime announceStartAt;
    private LocalDateTime announceEndAt;
    private EventStatus status;
    private String winnerPhoneHash;

    public enum EventStatus {
        READY,      // 준비중
        ACTIVE,     // 진행중
        ENDED       // 종료
    }
}
