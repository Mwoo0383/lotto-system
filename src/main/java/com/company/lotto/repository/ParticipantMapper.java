package com.company.lotto.repository;

import com.company.lotto.domain.Participant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ParticipantMapper {

    // 이벤트 참가자 등록
    void insertParticipant(Participant participant);

    // 참가자 단건 조회 (중복 참여 체크)
    Participant findByPhoneHashAndEventId(@Param("phoneHash") String phoneHash, @Param("eventId") Long eventId);

    // 다음 티켓 순번 계산
    int selectNextTicketSeq(Long eventId);
}
