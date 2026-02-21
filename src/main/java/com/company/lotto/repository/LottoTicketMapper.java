package com.company.lotto.repository;

import com.company.lotto.domain.LottoTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LottoTicketMapper {

    // 로또 번호 저장
    void insertTicket(LottoTicket ticket);

    // 특정 참가자의 로또 번호 조회
    LottoTicket findByParticipantId(@Param("participantId") Long participantId);
}
