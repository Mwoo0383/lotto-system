package com.company.lotto.repository;

import com.company.lotto.domain.NumberPool;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NumberPoolMapper {

    void batchInsert(List<NumberPool> pools);

    int countByEventId(Long eventId);
}
