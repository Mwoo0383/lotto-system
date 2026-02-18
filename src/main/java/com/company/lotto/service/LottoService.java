package com.company.lotto.service;

import com.company.lotto.domain.Event;
import com.company.lotto.domain.LottoTicket;
import com.company.lotto.domain.NumberPool;
import com.company.lotto.domain.NumberPool.PoolResult;
import com.company.lotto.domain.Participant;
import com.company.lotto.dto.ParticipateResponse;
import com.company.lotto.repository.EventMapper;
import com.company.lotto.repository.LottoTicketMapper;
import com.company.lotto.repository.NumberPoolMapper;
import com.company.lotto.repository.ParticipantMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LottoService {

    private final EventMapper eventMapper;
    private final ParticipantMapper participantMapper;
    private final NumberPoolMapper numberPoolMapper;
    private final LottoTicketMapper lottoTicketMapper;
    private final VerificationService verificationService;

    @Transactional
    public ParticipateResponse participate(String phoneNumber, Long eventId, Long verificationId) {
        // 1. 인증 상태 확인
        if (!verificationService.isVerified(verificationId)) {
            throw new IllegalStateException("인증이 완료되지 않았습니다.");
        }

        // 2. 이벤트 확인
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("존재하지 않는 이벤트입니다.");
        }
        if (event.getStatus() != Event.EventStatus.ACTIVE) {
            throw new IllegalStateException("진행 중인 이벤트가 아닙니다.");
        }

        // 3. 중복 체크
        String phoneHash = verificationService.hashPhone(phoneNumber);
        Participant existing = participantMapper.findByPhoneHashAndEventId(phoneHash, eventId);
        if (existing != null) {
            throw new IllegalStateException("이미 참가한 번호입니다.");
        }

        // 4. ticket_seq 부여 (현재 참가자 수 + 1)
        int ticketSeq = participantMapper.countByEventId(eventId) + 1;

        // 5. participant 저장
        String phoneLast4 = phoneNumber.substring(phoneNumber.length() - 4);
        Participant participant = new Participant();
        participant.setEventId(eventId);
        participant.setPhoneHash(phoneHash);
        participant.setPhoneEncrypted(phoneNumber); // TODO: 실제 암호화 적용
        participant.setPhoneLast4(phoneLast4);
        participant.setTicketSeq(ticketSeq);
        participantMapper.insertParticipant(participant);

        // 6. 슬롯 배정
        PoolResult targetResult = determineResult(phoneHash, event, ticketSeq);
        NumberPool slot = numberPoolMapper.findAvailableSlot(eventId, targetResult.name());

        // 당첨 슬롯이 소진된 경우 NONE으로 fallback
        if (slot == null && targetResult != PoolResult.NONE) {
            slot = numberPoolMapper.findAvailableSlot(eventId, PoolResult.NONE.name());
        }

        if (slot == null) {
            throw new IllegalStateException("배정 가능한 슬롯이 없습니다.");
        }

        numberPoolMapper.assignSlot(slot.getPoolId(), participant.getParticipantId());

        // 7. lotto_ticket 저장
        LottoTicket ticket = new LottoTicket();
        ticket.setParticipantId(participant.getParticipantId());
        ticket.setNum1(slot.getSlot1());
        ticket.setNum2(slot.getSlot2());
        ticket.setNum3(slot.getSlot3());
        ticket.setNum4(slot.getSlot4());
        ticket.setNum5(slot.getSlot5());
        ticket.setNum6(slot.getSlot6());
        lottoTicketMapper.insertTicket(ticket);

        List<Integer> lottoNumbers = List.of(
                slot.getSlot1(), slot.getSlot2(), slot.getSlot3(),
                slot.getSlot4(), slot.getSlot5(), slot.getSlot6()
        );

        // 8. 응답 반환
        ParticipateResponse response = new ParticipateResponse();
        response.setLottoNumbers(lottoNumbers);
        response.setPhoneLast4(phoneLast4);
        response.setMessage("로또 번호가 발급되었습니다!");
        return response;
    }

    public ParticipateResponse findResult(String phoneNumber, Long eventId) {
        String phoneHash = verificationService.hashPhone(phoneNumber);
        Participant participant = participantMapper.findByPhoneHashAndEventId(phoneHash, eventId);
        if (participant == null) {
            throw new IllegalArgumentException("참가 이력이 없습니다.");
        }

        LottoTicket ticket = lottoTicketMapper.findByParticipantId(participant.getParticipantId());
        if (ticket == null) {
            throw new IllegalStateException("발급된 로또 번호가 없습니다.");
        }

        List<Integer> lottoNumbers = List.of(
                ticket.getNum1(), ticket.getNum2(), ticket.getNum3(),
                ticket.getNum4(), ticket.getNum5(), ticket.getNum6()
        );

        ParticipateResponse response = new ParticipateResponse();
        response.setLottoNumbers(lottoNumbers);
        response.setPhoneLast4(participant.getPhoneLast4());
        response.setMessage("발급된 로또 번호입니다.");
        return response;
    }

    private PoolResult determineResult(String phoneHash, Event event, int ticketSeq) {
        if (phoneHash.equals(event.getWinnerPhoneHash())) {
            return PoolResult.FIRST;
        }
        if (ticketSeq >= 2000 && ticketSeq <= 7000) {
            return PoolResult.SECOND;
        }
        if (ticketSeq >= 1000 && ticketSeq <= 8000) {
            return PoolResult.THIRD;
        }
        return PoolResult.FOURTH;
    }
}
