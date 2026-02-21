package com.company.lotto.service;

import com.company.lotto.domain.Event;
import com.company.lotto.dto.event.AnnouncingEventResponse;
import com.company.lotto.dto.event.CreateEventRequest;
import com.company.lotto.dto.event.CreateEventResponse;
import com.company.lotto.dto.event.EventDetailResponse;
import com.company.lotto.dto.event.GetEventsResponse;
import com.company.lotto.repository.EventMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventMapper eventMapper;
    private final VerificationService verificationService;
    private final NumberPoolService numberPoolService;

    private final Clock clock; // 시간 주입(테스트 쉬움)

    public GetEventsResponse getEvents(int page, int size) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);

        int offset = (page - 1) * size;
        LocalDateTime now = LocalDateTime.now(clock);

        List<Event> events = eventMapper.findAllPaged(offset, size, now);
        int total = eventMapper.countAll();
        int totalPages = (int) Math.ceil((double) total / size);

        return new GetEventsResponse(events, page, size, total, totalPages);
    }

    @Transactional
    public CreateEventResponse createEvent(CreateEventRequest request) {
        validateEventTimeRule(request);

        Event event = new Event();
        event.setName(request.getName());
        event.setStartAt(request.getStartAt());
        event.setEndAt(request.getEndAt());
        event.setAnnounceStartAt(request.getAnnounceStartAt());
        event.setAnnounceEndAt(request.getAnnounceEndAt());

        // 원문 휴대폰 -> 해시 저장
        event.setWinnerPhoneHash(verificationService.hashPhone(request.getWinnerPhone()));

        event.setStatus(Event.EventStatus.READY);

        eventMapper.insertEvent(event);

        // 이벤트 생성 이후 번호 풀 생성(같은 트랜잭션)
        numberPoolService.generatePool(event.getEventId());

        return new CreateEventResponse(event.getEventId(), "이벤트가 등록되었습니다.");
    }

    public EventDetailResponse getEvent(Long eventId) {
        Event event = eventMapper.findById(eventId);
        if (event == null) return null;
        return EventDetailResponse.of(event);
    }

    public Event getActiveEvent() {
        return eventMapper.findActiveEvent();
    }

    public AnnouncingEventResponse getAnnouncingEvent() {
        LocalDateTime now = LocalDateTime.now(clock);
        Event event = eventMapper.findAnnouncingEvent(now);
        return event == null ? AnnouncingEventResponse.notAnnouncing() : AnnouncingEventResponse.of(event);
    }

    public void generatePool(Long eventId) {
        numberPoolService.generatePool(eventId);
    }

    private void validateEventTimeRule(CreateEventRequest req) {
        if (!req.getStartAt().isBefore(req.getEndAt())) {
            throw new IllegalArgumentException("참가 시작일은 참가 종료일보다 이전이어야 합니다.");
        }
        if (!req.getEndAt().isBefore(req.getAnnounceStartAt())) {
            throw new IllegalArgumentException("참가 종료일은 발표 시작일보다 이전이어야 합니다.");
        }
        if (!req.getAnnounceStartAt().isBefore(req.getAnnounceEndAt())) {
            throw new IllegalArgumentException("발표 시작일은 발표 종료일보다 이전이어야 합니다.");
        }
    }
}
