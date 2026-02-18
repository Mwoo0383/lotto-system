package com.company.lotto.repository;

import com.company.lotto.domain.Event;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventMapper {

    void insertEvent(Event event);

    List<Event> findAll();

    Event findById(Long eventId);

    Event findActiveEvent();

    void updateStatus(Long eventId, String status);
}
