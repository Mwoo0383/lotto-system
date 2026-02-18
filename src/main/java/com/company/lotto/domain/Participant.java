package com.company.lotto.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Participant {

    private Long participantId;
    private Long eventId;
    private String phoneHash;
    private String phoneEncrypted;
    private String phoneLast4;
    private Integer ticketSeq;
    private LocalDateTime createdAt;
}
