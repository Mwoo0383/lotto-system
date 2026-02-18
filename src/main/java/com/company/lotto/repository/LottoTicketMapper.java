package com.company.lotto.repository;

import com.company.lotto.domain.LottoTicket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LottoTicketMapper {

    void insertTicket(LottoTicket ticket);
}
