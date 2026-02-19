package com.company.lotto.service;

import com.company.lotto.domain.Event;
import com.company.lotto.domain.LottoTicket;
import com.company.lotto.domain.NumberPool;
import com.company.lotto.domain.NumberPool.PoolResult;
import com.company.lotto.domain.Participant;
import com.company.lotto.domain.ResultView;
import com.company.lotto.dto.ParticipateResponse;
import com.company.lotto.dto.ResultResponse;
import com.company.lotto.repository.EventMapper;
import com.company.lotto.repository.LottoTicketMapper;
import com.company.lotto.repository.NumberPoolMapper;
import com.company.lotto.repository.ParticipantMapper;
import com.company.lotto.repository.ResultViewMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final ResultViewMapper resultViewMapper;
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
        participant.setPhoneEncrypted(verificationService.encryptPhone(phoneNumber));
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

    private static final Map<PoolResult, String> RESULT_LABELS = Map.of(
            PoolResult.FIRST, "1등 당첨",
            PoolResult.SECOND, "2등 당첨",
            PoolResult.THIRD, "3등 당첨",
            PoolResult.FOURTH, "4등 당첨",
            PoolResult.NONE, "미당첨"
    );

    @Transactional
    public ResultResponse checkResult(String phoneNumber, Long eventId) {
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("존재하지 않는 이벤트입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (event.getAnnounceStartAt() == null || event.getAnnounceEndAt() == null) {
            throw new IllegalStateException("발표 기간이 설정되지 않은 이벤트입니다.");
        }
        if (now.isBefore(event.getAnnounceStartAt())) {
            throw new IllegalStateException("아직 발표 기간이 아닙니다.");
        }
        if (now.isAfter(event.getAnnounceEndAt())) {
            throw new IllegalStateException("발표 기간이 종료되었습니다.");
        }

        String phoneHash = verificationService.hashPhone(phoneNumber);
        Participant participant = participantMapper.findByPhoneHashAndEventId(phoneHash, eventId);
        if (participant == null) {
            throw new IllegalArgumentException("참가 이력이 없습니다.");
        }

        NumberPool pool = numberPoolMapper.findByParticipantId(participant.getParticipantId());
        if (pool == null) {
            throw new IllegalStateException("배정된 슬롯이 없습니다.");
        }

        ResultView resultView = resultViewMapper.findByParticipantId(participant.getParticipantId());
        boolean isFirstCheck = resultView == null;
        boolean won = pool.getResult() != PoolResult.NONE;

        ResultResponse response = new ResultResponse();
        response.setPhoneLast4(participant.getPhoneLast4());
        response.setWon(won);
        response.setFirstCheck(isFirstCheck);

        if (isFirstCheck) {
            response.setResultTier(pool.getResult().name());
            response.setResultLabel(RESULT_LABELS.get(pool.getResult()));

            LottoTicket ticket = lottoTicketMapper.findByParticipantId(participant.getParticipantId());
            if (ticket != null) {
                response.setLottoNumbers(List.of(
                        ticket.getNum1(), ticket.getNum2(), ticket.getNum3(),
                        ticket.getNum4(), ticket.getNum5(), ticket.getNum6()
                ));
            }

            ResultView newView = new ResultView();
            newView.setParticipantId(participant.getParticipantId());
            resultViewMapper.insert(newView);
        } else {
            resultViewMapper.incrementViewCount(participant.getParticipantId());
        }

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
