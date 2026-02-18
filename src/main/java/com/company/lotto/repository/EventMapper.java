package com.company.lotto.repository;

import com.company.lotto.domain.Event;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventMapper {

    Event findById(Long eventId);

    Event findActiveEvent();

    void updateStatus(Long eventId, String status);
}
