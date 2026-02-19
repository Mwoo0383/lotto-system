package com.company.lotto.domain;

import com.company.lotto.domain.NumberPool.PoolResult;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LottoTicket {

    private Long ticketId;
    private Long participantId;
    private Integer num1;
    private Integer num2;
    private Integer num3;
    private Integer num4;
    private Integer num5;
    private Integer num6;
    private PoolResult result;
    private LocalDateTime issuedAt;
}
