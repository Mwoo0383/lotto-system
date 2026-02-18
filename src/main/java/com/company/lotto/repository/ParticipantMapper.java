package com.company.lotto.repository;

import com.company.lotto.domain.Participant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ParticipantMapper {

    void insertParticipant(Participant participant);

    Participant findByPhoneHashAndEventId(String phoneHash, Long eventId);
}
