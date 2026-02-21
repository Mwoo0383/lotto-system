package com.company.lotto.dto.event;

import com.company.lotto.domain.Event;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GetEventsResponse {
    private List<Event> events;
    private int page;
    private int size;
    private int total;
    private int totalPages;
}
