package com.company.lotto.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateEventRequest {

    @NotBlank(message = "이벤트 이름은 필수입니다.")
    private String name;

    @NotBlank(message = "1등 당첨자 휴대폰 번호는 필수입니다.")
    private String winnerPhone;

    @NotNull(message = "참가 시작일시는 필수입니다.")
    private LocalDateTime startAt;

    @NotNull(message = "참가 종료일시는 필수입니다.")
    private LocalDateTime endAt;

    @NotNull(message = "발표 시작일시는 필수입니다.")
    private LocalDateTime announceStartAt;

    @NotNull(message = "발표 종료일시는 필수입니다.")
    private LocalDateTime announceEndAt;
}
