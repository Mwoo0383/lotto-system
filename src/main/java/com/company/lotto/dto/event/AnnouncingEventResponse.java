package com.company.lotto.dto.event;

import com.company.lotto.domain.Event;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnnouncingEventResponse {
    private boolean announcing;
    private Long eventId;
    private String name;
    private LocalDateTime announceStartAt;
    private LocalDateTime announceEndAt;

    public static AnnouncingEventResponse notAnnouncing() {
        return new AnnouncingEventResponse(false, null, null, null, null);
    }

    public static AnnouncingEventResponse of(Event e) {
        return new AnnouncingEventResponse(true, e.getEventId(), e.getName(), e.getAnnounceStartAt(), e.getAnnounceEndAt());
    }
}
