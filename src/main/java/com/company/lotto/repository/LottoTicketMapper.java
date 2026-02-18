package com.company.lotto.repository;

import com.company.lotto.domain.LottoTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LottoTicketMapper {

    void insertTicket(LottoTicket ticket);

    LottoTicket findByParticipantId(@Param("participantId") Long participantId);
}
