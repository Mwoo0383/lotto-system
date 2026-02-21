package com.company.lotto.repository;

import com.company.lotto.domain.ResultView;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResultViewMapper {

    // 참가자 기준 결과 조회 이력 조회
    ResultView findByParticipantId(@Param("participantId") Long participantId);

    // 결과 최초 조회 기록 생성
    void insert(ResultView resultView);

    void upsertView(@Param("participantId") Long participantId, @Param("now") LocalDateTime now);
}
