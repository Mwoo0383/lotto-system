package com.company.lotto.dto;

import com.company.lotto.domain.Event;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActiveEventResponse {
    private boolean active;
    private Long eventId;
    private String name;
    private Event.EventStatus status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    public static ActiveEventResponse inactive() {
        return new ActiveEventResponse(false, null, null, null, null, null);
    }

    public static ActiveEventResponse of(Event e) {
        return new ActiveEventResponse(true, e.getEventId(), e.getName(), e.getStatus(), e.getStartAt(), e.getEndAt());
    }
}
