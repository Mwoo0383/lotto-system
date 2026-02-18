package com.company.lotto.repository;

import com.company.lotto.domain.Participant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ParticipantMapper {

    void insertParticipant(Participant participant);

    Participant findByPhoneHashAndEventId(@Param("phoneHash") String phoneHash, @Param("eventId") Long eventId);

    int countByEventId(Long eventId);
}
