package com.company.lotto.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LottoResult {

    private Long resultId;
    private Long participantId;
    private Long poolId;
    private NumberPool.PoolResult result;
    private Integer matchedCount;
}
