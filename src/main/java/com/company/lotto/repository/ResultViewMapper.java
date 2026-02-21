package com.company.lotto.repository;

import com.company.lotto.domain.ResultView;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResultViewMapper {

    ResultView findByParticipantId(@Param("participantId") Long participantId);

    void insert(ResultView resultView);

    void incrementViewCount(@Param("participantId") Long participantId, @Param("lastViewAt") LocalDateTime lastViewAt);
}
