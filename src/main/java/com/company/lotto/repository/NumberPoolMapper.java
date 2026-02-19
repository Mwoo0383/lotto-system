package com.company.lotto.repository;

import com.company.lotto.domain.NumberPool;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NumberPoolMapper {

    void batchInsert(List<NumberPool> pools);

    int countByEventId(Long eventId);

    NumberPool findAvailableSlot(@Param("eventId") Long eventId, @Param("result") String result);

    NumberPool findRandomAvailableSlot(@Param("eventId") Long eventId, @Param("results") List<String> results);

    void markUsed(@Param("poolId") Long poolId);
}
