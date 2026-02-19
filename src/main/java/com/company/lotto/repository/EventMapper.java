package com.company.lotto.repository;

import com.company.lotto.domain.Event;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventMapper {

    void insertEvent(Event event);

    List<Event> findAll();

    List<Event> findAllPaged(@Param("offset") int offset, @Param("limit") int limit);

    int countAll();

    Event findById(Long eventId);

    Event findActiveEvent();

    Event findAnnouncingEvent();

    void updateStatus(Long eventId, String status);
}
