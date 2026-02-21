package com.company.lotto.repository;

import com.company.lotto.domain.NumberPool;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NumberPoolMapper {

    // 번호 풀 대량 생성
    void batchInsert(List<NumberPool> pools);

    // 특정 이벤트의 번호 풀 총 개수 조회
    int countByEventId(Long eventId);

    // 사용 가능한 번호 슬롯 1개 랜덤 조회
    NumberPool findRandomAvailableSlot(@Param("eventId") Long eventId, @Param("results") List<String> results);

    // 번호 풀 사용 처리
    void markUsed(@Param("poolId") Long poolId);
}
