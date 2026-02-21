package com.company.lotto.repository;

import com.company.lotto.domain.Event;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventMapper {

    // 이벤트 생성
    void insertEvent(Event event);

    // 전체 이벤트 목록 조회
    List<Event> findAll();

    // 페이징 + 상태 기반 정렬 조회
    List<Event> findAllPaged(@Param("offset") int offset, @Param("limit") int limit, @Param("now") LocalDateTime now);

    // 전체 이벤트 개수 조회
    int countAll();

    // 이벤트 단건 조회
    Event findById(Long eventId);

    // 현재 활성화된 이벤트 조회 - 최근 1건
    Event findActiveEvent();

    // 당첨자 발표 중인 이벤트 조회 - 최근 1건
    Event findAnnouncingEvent(@Param("now") LocalDateTime now);

    // 이벤트 상태 변경
    void updateStatus(Long eventId, String status);
}
