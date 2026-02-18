package com.company.lotto.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NumberPool {

    private Long poolId;
    private Integer slot1;
    private Integer slot2;
    private Integer slot3;
    private Integer slot4;
    private Integer slot5;
    private Integer slot6;
    private PoolResult result;
    private Integer isUsed;
    private Long eventId;
    private Long usedParticipantId;

    public enum PoolResult {
        FIRST,      // 1등
        SECOND,     // 2등
        THIRD,      // 3등
        FOURTH,     // 4등
        NONE        // 미당첨
    }
}
