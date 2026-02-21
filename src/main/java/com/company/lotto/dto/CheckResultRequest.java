package com.company.lotto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CheckResultRequest {

    @NotBlank(message = "휴대폰 번호는 필수입니다.")
    private String phoneNumber;

    @NotNull(message = "eventId는 필수입니다.")
    private Long eventId;
}
