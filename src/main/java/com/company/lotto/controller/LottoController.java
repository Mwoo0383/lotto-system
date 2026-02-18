package com.company.lotto.controller;

import com.company.lotto.domain.Event;
import com.company.lotto.dto.ParticipateRequest;
import com.company.lotto.dto.ParticipateResponse;
import com.company.lotto.dto.ResultResponse;
import com.company.lotto.dto.VerificationCodeRequest;
import com.company.lotto.dto.VerificationRequest;
import com.company.lotto.repository.EventMapper;
import com.company.lotto.service.LottoService;
import com.company.lotto.service.NumberPoolService;
import com.company.lotto.service.VerificationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LottoController {

    private final EventMapper eventMapper;
    private final VerificationService verificationService;
    private final LottoService lottoService;
    private final NumberPoolService numberPoolService;

    @GetMapping("/events")
    public ResponseEntity<?> getEvents() {
        return ResponseEntity.ok(eventMapper.findAll());
    }

    @PostMapping("/event")
    public ResponseEntity<?> createEvent(@RequestBody Event event) {
        try {
            if (event.getWinnerPhoneHash() == null || event.getWinnerPhoneHash().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "1등 당첨자 휴대폰 번호는 필수입니다."));
            }
            event.setWinnerPhoneHash(verificationService.hashPhone(event.getWinnerPhoneHash()));
            event.setStatus(Event.EventStatus.READY);
            eventMapper.insertEvent(event);
            numberPoolService.generatePool(event.getEventId());
            return ResponseEntity.ok(Map.of("eventId", event.getEventId(), "message", "이벤트가 등록되었습니다."));
        } catch (Exception e) {
            log.error("이벤트 등록 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage(e)));
        }
    }

    @GetMapping("/event/active")
    public ResponseEntity<?> getActiveEvent() {
        Event event = eventMapper.findActiveEvent();
        if (event == null) {
            return ResponseEntity.ok(Map.of("active", false));
        }
        return ResponseEntity.ok(Map.of(
                "active", true,
                "eventId", event.getEventId(),
                "name", event.getName(),
                "status", event.getStatus(),
                "startAt", event.getStartAt().toString(),
                "endAt", event.getEndAt().toString()
        ));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<?> getEvent(@PathVariable Long eventId) {
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            return ResponseEntity.notFound().build();
        }
        var result = new java.util.HashMap<String, Object>();
        result.put("eventId", event.getEventId());
        result.put("name", event.getName());
        result.put("status", event.getStatus());
        result.put("startAt", event.getStartAt().toString());
        result.put("endAt", event.getEndAt().toString());
        if (event.getAnnounceStartAt() != null) {
            result.put("announceStartAt", event.getAnnounceStartAt().toString());
        }
        if (event.getAnnounceEndAt() != null) {
            result.put("announceEndAt", event.getAnnounceEndAt().toString());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/event/{eventId}/generate-pool")
    public ResponseEntity<?> generatePool(@PathVariable Long eventId) {
        try {
            numberPoolService.generatePool(eventId);
            return ResponseEntity.ok(Map.of("message", "로또 번호 풀 생성 완료", "eventId", eventId));
        } catch (Exception e) {
            log.error("풀 생성 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage(e)));
        }
    }

    @PostMapping("/verification/send")
    public ResponseEntity<?> sendVerification(@RequestBody VerificationRequest request) {
        try {
            Map<String, Object> result = verificationService.sendCode(request.getPhoneNumber(), request.getEventId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("인증번호 발송 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage(e)));
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
        } catch (Exception e) {
            log.error("참가 처리 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage(e)));
        }
    }

    @PostMapping("/lotto/result")
    public ResponseEntity<?> checkResult(@RequestBody ParticipateRequest request) {
        try {
            ResultResponse response = lottoService.checkResult(
                    request.getPhoneNumber(),
                    request.getEventId()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("결과 조회 실패", e);
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage(e)));
        }
    }

    @GetMapping("/event/announcing")
    public ResponseEntity<?> getAnnouncingEvent() {
        Event event = eventMapper.findAnnouncingEvent();
        if (event == null) {
            return ResponseEntity.ok(Map.of("announcing", false));
        }
        return ResponseEntity.ok(Map.of(
                "announcing", true,
                "eventId", event.getEventId(),
                "name", event.getName(),
                "announceStartAt", event.getAnnounceStartAt().toString(),
                "announceEndAt", event.getAnnounceEndAt().toString()
        ));
    }

    private String errorMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }
}
