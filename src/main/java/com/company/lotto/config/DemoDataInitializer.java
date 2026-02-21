package com.company.lotto.config;

import com.company.lotto.domain.Event;
import com.company.lotto.repository.EventMapper;
import com.company.lotto.service.NumberPoolService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("h2")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private final EventMapper eventMapper;
    private final NumberPoolService numberPoolService;

    @Override
    public void run(ApplicationArguments args) {
        List<Event> events = eventMapper.findAll();
        for (Event event : events) {
            if (event.getStatus() == Event.EventStatus.READY) {
                log.info("데모 이벤트 번호풀 생성 중: {} (ID: {})", event.getName(), event.getEventId());
                numberPoolService.generatePool(event.getEventId());
                log.info("데모 이벤트 활성화 완료: {} (ID: {})", event.getName(), event.getEventId());
            }
        }
    }
}
