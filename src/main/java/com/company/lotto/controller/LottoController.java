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
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LottoController {

    private final EventMapper eventMapper;
    private final VerificationService verificationService;
    private final LottoService lottoService;
    private final NumberPoolService numberPoolService;

    @GetMapping("/events")
    public ResponseEntity<?> getEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (size <= 0) size = 10;
        int offset = (page - 1) * size;
        var events = eventMapper.findAllPaged(offset, size, LocalDateTime.now());
        int total = eventMapper.countAll();
        int totalPages = (int) Math.ceil((double) total / size);
        return ResponseEntity.ok(Map.of(
                "events", events,
                "page", page,
                "size", size,
                "total", total,
                "totalPages", totalPages
        ));
    }

    @PostMapping("/event")
    public ResponseEntity<?> createEvent(@RequestBody Event event) {
        if (event.getWinnerPhoneHash() == null || event.getWinnerPhoneHash().isBlank()) {
            throw new IllegalArgumentException("1등 당첨자 휴대폰 번호는 필수입니다.");
        }
        if (event.getStartAt() == null || event.getEndAt() == null
                || event.getAnnounceStartAt() == null || event.getAnnounceEndAt() == null) {
            throw new IllegalArgumentException("참가 시작/종료, 발표 시작/종료 일시는 필수입니다.");
        }
        if (!event.getStartAt().isBefore(event.getEndAt())) {
            throw new IllegalArgumentException("참가 시작일은 참가 종료일보다 이전이어야 합니다.");
        }
        if (!event.getEndAt().isBefore(event.getAnnounceStartAt())) {
            throw new IllegalArgumentException("참가 종료일은 발표 시작일보다 이전이어야 합니다.");
        }
        if (!event.getAnnounceStartAt().isBefore(event.getAnnounceEndAt())) {
            throw new IllegalArgumentException("발표 시작일은 발표 종료일보다 이전이어야 합니다.");
        }
        event.setWinnerPhoneHash(verificationService.hashPhone(event.getWinnerPhoneHash()));
        event.setStatus(Event.EventStatus.READY);
        eventMapper.insertEvent(event);
        numberPoolService.generatePool(event.getEventId());
        return ResponseEntity.ok(Map.of("eventId", event.getEventId(), "message", "이벤트가 등록되었습니다."));
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
        numberPoolService.generatePool(eventId);
        return ResponseEntity.ok(Map.of("message", "로또 번호 풀 생성 완료", "eventId", eventId));
    }

    @PostMapping("/verification/send")
    public ResponseEntity<?> sendVerification(@RequestBody VerificationRequest request) {
        Map<String, Object> result = verificationService.sendCode(request.getPhoneNumber(), request.getEventId());
        return ResponseEntity.ok(result);
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
        ParticipateResponse response = lottoService.participate(
                request.getPhoneNumber(),
                request.getEventId(),
                request.getVerificationId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/lotto/result")
    public ResponseEntity<?> checkResult(@RequestBody ParticipateRequest request) {
        ResultResponse response = lottoService.checkResult(
                request.getPhoneNumber(),
                request.getEventId()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/event/announcing")
    public ResponseEntity<?> getAnnouncingEvent() {
        Event event = eventMapper.findAnnouncingEvent(LocalDateTime.now());
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
}
