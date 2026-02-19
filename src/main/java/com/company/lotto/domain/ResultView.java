package com.company.lotto.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResultView {

    private Long participantId;
    private Integer viewCount;
    private LocalDateTime firstViewAt;
    private LocalDateTime lastViewAt;
}
