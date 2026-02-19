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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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

        // 3. 중복 체크 (애플리케이션 레벨)
        String phoneHash = verificationService.hashPhone(phoneNumber);
        Participant existing = participantMapper.findByPhoneHashAndEventId(phoneHash, eventId);
        if (existing != null) {
            throw new IllegalStateException("이미 참가한 번호입니다.");
        }

        // 4. ticket_seq 부여 (FOR UPDATE로 동시성 안전)
        int ticketSeq = participantMapper.selectNextTicketSeq(eventId);

        // 5. participant 저장 (UNIQUE INDEX로 race condition 최종 방어)
        String phoneLast4 = phoneNumber.substring(phoneNumber.length() - 4);
        Participant participant = new Participant();
        participant.setEventId(eventId);
        participant.setPhoneHash(phoneHash);
        participant.setPhoneEncrypted(verificationService.encryptPhone(phoneNumber));
        participant.setPhoneLast4(phoneLast4);
        participant.setTicketSeq(ticketSeq);
        try {
            participantMapper.insertParticipant(participant);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("이미 참가한 번호입니다.");
        }

        // 6. 슬롯 배정 (자격 범위 내 남은 슬롯에서 확률적 배정)
        List<String> eligibleResults = getEligibleResults(phoneHash, event, ticketSeq);
        NumberPool slot = numberPoolMapper.findRandomAvailableSlot(eventId, eligibleResults);

        if (slot == null) {
            throw new IllegalStateException("배정 가능한 슬롯이 없습니다.");
        }

        numberPoolMapper.markUsed(slot.getPoolId());

        // 7. lotto_ticket 저장 (번호 + 결과)
        LottoTicket ticket = new LottoTicket();
        ticket.setParticipantId(participant.getParticipantId());
        ticket.setNum1(slot.getSlot1());
        ticket.setNum2(slot.getSlot2());
        ticket.setNum3(slot.getSlot3());
        ticket.setNum4(slot.getSlot4());
        ticket.setNum5(slot.getSlot5());
        ticket.setNum6(slot.getSlot6());
        ticket.setResult(slot.getResult());
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

        LottoTicket ticket = lottoTicketMapper.findByParticipantId(participant.getParticipantId());
        if (ticket == null) {
            throw new IllegalStateException("발급된 티켓이 없습니다.");
        }

        ResultView resultView = resultViewMapper.findByParticipantId(participant.getParticipantId());
        boolean isFirstCheck = resultView == null;
        boolean won = ticket.getResult() != PoolResult.NONE;

        ResultResponse response = new ResultResponse();
        response.setPhoneLast4(participant.getPhoneLast4());
        response.setWon(won);
        response.setFirstCheck(isFirstCheck);

        if (isFirstCheck) {
            response.setResultTier(ticket.getResult().name());
            response.setResultLabel(RESULT_LABELS.get(ticket.getResult()));
            response.setLottoNumbers(List.of(
                    ticket.getNum1(), ticket.getNum2(), ticket.getNum3(),
                    ticket.getNum4(), ticket.getNum5(), ticket.getNum6()
            ));

            ResultView newView = new ResultView();
            newView.setParticipantId(participant.getParticipantId());
            resultViewMapper.insert(newView);
        } else {
            resultViewMapper.incrementViewCount(participant.getParticipantId());
        }

        return response;
    }

    List<String> getEligibleResults(String phoneHash, Event event, int ticketSeq) {
        List<String> eligible = new ArrayList<>();

        if (phoneHash.equals(event.getWinnerPhoneHash())) {
            eligible.add(PoolResult.FIRST.name());
        }
        if (ticketSeq >= 2000 && ticketSeq <= 7000) {
            eligible.add(PoolResult.SECOND.name());
        }
        if (ticketSeq >= 1000 && ticketSeq <= 8000) {
            eligible.add(PoolResult.THIRD.name());
        }
        eligible.add(PoolResult.FOURTH.name());
        eligible.add(PoolResult.NONE.name());

        return eligible;
    }
}
