package com.company.lotto.controller;

import com.company.lotto.domain.Event;
import com.company.lotto.dto.ParticipateRequest;
import com.company.lotto.dto.ParticipateResponse;
import com.company.lotto.dto.VerificationCodeRequest;
import com.company.lotto.dto.VerificationRequest;
import com.company.lotto.repository.EventMapper;
import com.company.lotto.service.LottoService;
import com.company.lotto.service.NumberPoolService;
import com.company.lotto.service.VerificationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LottoController {

    private final EventMapper eventMapper;
    private final VerificationService verificationService;
    private final LottoService lottoService;
    private final NumberPoolService numberPoolService;

    @GetMapping("/event/{eventId}")
    public ResponseEntity<?> getEvent(@PathVariable Long eventId) {
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "eventId", event.getEventId(),
                "name", event.getName(),
                "status", event.getStatus(),
                "startAt", event.getStartAt().toString(),
                "endAt", event.getEndAt().toString()
        ));
    }

    @PostMapping("/event/{eventId}/generate-pool")
    public ResponseEntity<?> generatePool(@PathVariable Long eventId) {
        try {
            numberPoolService.generatePool(eventId);
            return ResponseEntity.ok(Map.of("message", "로또 번호 풀 생성 완료", "eventId", eventId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verification/send")
    public ResponseEntity<?> sendVerification(@RequestBody VerificationRequest request) {
        try {
            Map<String, Object> result = verificationService.sendCode(request.getPhoneNumber(), request.getEventId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verification/verify")
    public ResponseEntity<?> verifyCode(@RequestBody VerificationCodeRequest request) {
        boolean verified = verificationService.verifyCode(request.getVerificationId(), request.getCode());
        if (verified) {
            return ResponseEntity.ok(Map.of("verified", true));
        }
        return ResponseEntity.badRequest().body(Map.of("verified", false, "error", "인증번호가 일치하지 않거나 만료되었습니다."));
    }

    @PostMapping("/lotto/participate")
    public ResponseEntity<?> participate(@RequestBody ParticipateRequest request) {
        try {
            ParticipateResponse response = lottoService.participate(
                    request.getPhoneNumber(),
                    request.getEventId(),
                    request.getVerificationId()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
