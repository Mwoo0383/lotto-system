package com.company.lotto.controller;

import com.company.lotto.domain.Event;
import com.company.lotto.dto.ActiveEventResponse;
import com.company.lotto.dto.CheckResultRequest;
import com.company.lotto.dto.CreateEventRequest;
import com.company.lotto.dto.CreateEventResponse;
import com.company.lotto.dto.EventDetailResponse;
import com.company.lotto.dto.GetEventsResponse;
import com.company.lotto.dto.ParticipateRequest;
import com.company.lotto.dto.ParticipateResponse;
import com.company.lotto.dto.ResultResponse;
import com.company.lotto.dto.VerificationCodeRequest;
import com.company.lotto.dto.VerificationRequest;
import com.company.lotto.service.EventService;
import com.company.lotto.service.LottoService;
import com.company.lotto.service.VerificationService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LottoController {

    private final EventService eventService;
    private final VerificationService verificationService;
    private final LottoService lottoService;

    @GetMapping("/events")
    public ResponseEntity<GetEventsResponse> getEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(eventService.getEvents(page, size));
    }

    @PostMapping("/events")
    public ResponseEntity<CreateEventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.ok(eventService.createEvent(request));
    }

    @GetMapping("/events/active")
    public ResponseEntity<ActiveEventResponse> getActiveEvent() {
        Event event = eventService.getActiveEvent();
        return ResponseEntity.ok(event == null ? ActiveEventResponse.inactive() : ActiveEventResponse.of(event));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<?> getEvent(@PathVariable Long eventId) {
        EventDetailResponse response = eventService.getEvent(eventId);
        if (response == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/events/{eventId}/generate-pool")
    public ResponseEntity<Map<String, Object>> generatePool(@PathVariable Long eventId) {
        eventService.generatePool(eventId);
        return ResponseEntity.ok(Map.of("message", "로또 번호 풀 생성 완료", "eventId", eventId));
    }

    @GetMapping("/events/announcing")
    public ResponseEntity<?> getAnnouncingEvent() {
        return ResponseEntity.ok(eventService.getAnnouncingEvent());
    }

    @PostMapping("/verification/send")
    public ResponseEntity<?> sendVerification(@Valid @RequestBody VerificationRequest request) {
        return ResponseEntity.ok(verificationService.sendCode(request.getPhoneNumber(), request.getEventId()));
    }

    @PostMapping("/verification/verify")
    public ResponseEntity<?> verifyCode(@Valid @RequestBody VerificationCodeRequest request) {
        boolean verified = verificationService.verifyCode(request.getVerificationId(), request.getCode());
        if (verified) return ResponseEntity.ok(Map.of("verified", true));
        return ResponseEntity.badRequest().body(Map.of("verified", false, "error", "인증번호가 일치하지 않거나 만료되었습니다."));
    }

    @PostMapping("/lotto/participate")
    public ResponseEntity<ParticipateResponse> participate(@Valid @RequestBody ParticipateRequest request) {
        ParticipateResponse response = lottoService.participate(
                request.getPhoneNumber(),
                request.getEventId(),
                request.getVerificationId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/lotto/result")
    public ResponseEntity<ResultResponse> checkResult(@Valid @RequestBody CheckResultRequest request) {
        ResultResponse response = lottoService.checkResult(
                request.getPhoneNumber(),
                request.getEventId()
        );
        return ResponseEntity.ok(response);
    }
}
