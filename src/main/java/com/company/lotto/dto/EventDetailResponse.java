package com.company.lotto.dto;

import com.company.lotto.domain.Event;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventDetailResponse {
    private Long eventId;
    private String name;
    private Event.EventStatus status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime announceStartAt;
    private LocalDateTime announceEndAt;

    public static EventDetailResponse of(Event e) {
        return new EventDetailResponse(
                e.getEventId(),
                e.getName(),
                e.getStatus(),
                e.getStartAt(),
                e.getEndAt(),
                e.getAnnounceStartAt(),
                e.getAnnounceEndAt()
        );
    }
}
