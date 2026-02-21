package com.company.lotto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateEventResponse {
    private Long eventId;
    private String message;
}
