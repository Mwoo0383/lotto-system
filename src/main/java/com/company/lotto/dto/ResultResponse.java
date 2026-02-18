package com.company.lotto.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResultResponse {
    private boolean won;
    private boolean firstCheck;
    private String resultTier;
    private String resultLabel;
    private List<Integer> lottoNumbers;
    private String phoneLast4;
}
