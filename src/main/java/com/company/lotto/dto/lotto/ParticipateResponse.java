package com.company.lotto.dto.lotto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticipateResponse {
    private List<Integer> lottoNumbers;
    private String phoneLast4;
    private String message;
    private boolean alreadyIssued;
}
