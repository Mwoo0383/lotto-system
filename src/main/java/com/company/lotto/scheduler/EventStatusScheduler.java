package com.company.lotto.scheduler;

import com.company.lotto.repository.EventMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventStatusScheduler {

    private final EventMapper eventMapper;
    private final Clock clock;

    /**
     * 1분마다 이벤트 상태 자동 갱신
     * - READY -> ACTIVE
     * - ACTIVE -> ENDED
     */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void refreshEventStatus() {
        LocalDateTime now = LocalDateTime.now(clock);

        int activated = eventMapper.activateReadyEvents(now);
        int ended = eventMapper.endActiveEvents(now);

        if (activated > 0 || ended > 0) {
            log.info("이벤트 상태 자동 변경: activated={}, ended={}, now={}", activated, ended, now);
        }
    }
}
