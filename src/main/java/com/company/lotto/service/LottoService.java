package com.company.lotto.service;

import com.company.lotto.domain.Event;
import com.company.lotto.domain.LottoTicket;
import com.company.lotto.domain.NumberPool;
import com.company.lotto.domain.NumberPool.PoolResult;
import com.company.lotto.domain.Participant;
import com.company.lotto.domain.ResultView;
import com.company.lotto.dto.lotto.ParticipateResponse;
import com.company.lotto.dto.lotto.ResultResponse;
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

    // 이벤트 조회/검증
    private final EventMapper eventMapper;

    // 참가자(전화번호 기반) 저장/조회
    private final ParticipantMapper participantMapper;

    // 로또 번호 풀(재고) 조회/잠금/사용처리
    private final NumberPoolMapper numberPoolMapper;

    // 실제 발급된 티켓 저장/조회
    private final LottoTicketMapper lottoTicketMapper;

    // 결과 조회 이력(첫 조회인지, 조회수 등) 관리
    private final ResultViewMapper resultViewMapper;

    // 인증 상태 확인 + 전화번호 해시/암호화 처리
    private final VerificationService verificationService;

    /**
     * 이벤트 참여(로또 번호 발급)
     *
     * 핵심 흐름:
     * 1) 인증 완료 여부 확인
     * 2) 이벤트 존재/상태(ACTIVE) 확인
     * 3) 전화번호 해시 기반 중복 참여 체크 (이벤트 단위)
     * 4) Participant 저장 + ticket_seq 발급 (동시성 충돌 시 재시도)
     * 5) NumberPool에서 자격 조건에 맞는 슬롯(번호+등수)을 잠금 조회 후 사용 처리
     * 6) LottoTicket에 발급 결과 저장
     * 7) 사용자에게 번호 + 마스킹 정보 응답
     */
    @Transactional
    public ParticipateResponse participate(String phoneNumber, Long eventId, Long verificationId) {
        // 1. 인증 상태 확인 (인증이 완료되지 않으면 참여 불가)
        if (!verificationService.isVerified(verificationId)) {
            throw new IllegalStateException("인증이 완료되지 않았습니다.");
        }

        // 2. 이벤트 확인 (존재 여부 + 진행 중인지)
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("존재하지 않는 이벤트입니다.");
        }
        if (event.getStatus() != Event.EventStatus.ACTIVE) {
            throw new IllegalStateException("진행 중인 이벤트가 아닙니다.");
        }

        // 3. 중복 체크
        // - phoneNumber를 hash 처리해서 저장/비교 (개인정보 보호 + 중복 방지)
        String phoneHash = verificationService.hashPhone(phoneNumber);
        Participant existing = participantMapper.findByPhoneHashAndEventId(phoneHash, eventId);
        if (existing != null) {
            throw new IllegalStateException("이미 참가한 번호입니다.");
        }

        // 4. participant 저장 준비
        String phoneLast4 = phoneNumber.substring(phoneNumber.length() - 4);
        String phoneEncrypted = verificationService.encryptPhone(phoneNumber);

        Participant participant = new Participant();
        participant.setEventId(eventId);
        participant.setPhoneHash(phoneHash);
        participant.setPhoneEncrypted(phoneEncrypted);
        participant.setPhoneLast4(phoneLast4);

        // ticket_seq는 MAX + 1 기반이라 동시에 여러 요청이 오면 충돌 가능
        // - DB에 UNIQUE 인덱스로 방어하고,
        // - 충돌 발생 시 최대 3번 재시도
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            int ticketSeq = participantMapper.selectNextTicketSeq(eventId);
            participant.setTicketSeq(ticketSeq);
            participant.setCreatedAt(LocalDateTime.now());

            try {
                participantMapper.insertParticipant(participant);
                break; // 저장 성공하면 루프 종료
            } catch (DuplicateKeyException e) {
                if (participantMapper.findByPhoneHashAndEventId(phoneHash, eventId) != null) {
                    throw new IllegalStateException("이미 참가한 번호입니다.");
                }
                // 재시도 끝까지 실패하면 사용자에게 재시도 안내
                if (attempt == maxRetries - 1) {
                    throw new IllegalStateException("참가 처리 중 오류가 발생했습니다. 다시 시도해주세요.");
                }
            }
        }

        // 5. 슬롯 배정 (NumberPool에서 번호 + 등수 결과 가져오기)
        List<String> eligibleResults = getEligibleResults(phoneHash, event, participant.getTicketSeq());
        NumberPool slot = numberPoolMapper.findRandomAvailableSlot(eventId, eligibleResults);

        if (slot == null) {
            throw new IllegalStateException("배정 가능한 슬롯이 없습니다.");
        }

        // 선택된 슬롯을 사용 처리 (재사용 방지)
        numberPoolMapper.markUsed(slot.getPoolId());

        // 6. lotto_ticket 저장 (실제 발급된 티켓 기록)
        LottoTicket ticket = new LottoTicket();
        ticket.setParticipantId(participant.getParticipantId());
        ticket.setNum1(slot.getSlot1());
        ticket.setNum2(slot.getSlot2());
        ticket.setNum3(slot.getSlot3());
        ticket.setNum4(slot.getSlot4());
        ticket.setNum5(slot.getSlot5());
        ticket.setNum6(slot.getSlot6());
        ticket.setResult(slot.getResult());
        ticket.setIssuedAt(LocalDateTime.now());
        lottoTicketMapper.insertTicket(ticket);

        // 사용자 응답용 로또 번호 리스트 구성
        List<Integer> lottoNumbers = List.of(
                slot.getSlot1(), slot.getSlot2(), slot.getSlot3(),
                slot.getSlot4(), slot.getSlot5(), slot.getSlot6()
        );

        // 7. 응답 반환 (번호 + 전화번호 뒷자리 + 안내 메시지)
        ParticipateResponse response = new ParticipateResponse();
        response.setLottoNumbers(lottoNumbers);
        response.setPhoneLast4(phoneLast4);
        response.setMessage("로또 번호가 발급되었습니다!");
        return response;
    }

    // 결과 enum을 사용자에게 보여줄 라벨로 매핑
    private static final Map<PoolResult, String> RESULT_LABELS = Map.of(
            PoolResult.FIRST, "1등 당첨",
            PoolResult.SECOND, "2등 당첨",
            PoolResult.THIRD, "3등 당첨",
            PoolResult.FOURTH, "4등 당첨",
            PoolResult.NONE, "미당첨"
    );

    /**
     * 결과 확인 API
     *
     * 핵심 정책:
     * - 발표 기간(announce_start_at ~ announce_end_at) 안에서만 결과 조회 가능
     * - 결과 상세(등수/번호)는 "첫 조회"일 때만 공개
     * - 조회 이력(result_view)은 UPSERT로 기록/증가하여 동시성 안전하게 관리
     */
    @Transactional
    public ResultResponse checkResult(String phoneNumber, Long eventId) {
        // 1. 이벤트 확인
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("존재하지 않는 이벤트입니다.");
        }

        // 2. 발표 기간 검증 (이 기간에만 결과 조회 허용)
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

        // 3. 참가자 확인 (전화번호 hash + 이벤트로 참가 이력 조회)
        String phoneHash = verificationService.hashPhone(phoneNumber);
        Participant participant = participantMapper.findByPhoneHashAndEventId(phoneHash, eventId);
        if (participant == null) {
            throw new IllegalArgumentException("참가 이력이 없습니다.");
        }

        // 4. 티켓 확인 (참가자가 발급받은 티켓 조회)
        LottoTicket ticket = lottoTicketMapper.findByParticipantId(participant.getParticipantId());
        if (ticket == null) {
            throw new IllegalStateException("발급된 티켓이 없습니다.");
        }

        // 당첨 여부 판단 (미당첨이 아니면 당첨)
        boolean won = ticket.getResult() != PoolResult.NONE;

        // 5. 결과 조회 이력 기록 (UPSERT)
        // - 없으면 insert(view_count=1)
        // - 있으면 update(view_count + 1)
        LocalDateTime viewNow = LocalDateTime.now();
        resultViewMapper.upsertView(participant.getParticipantId(), viewNow);

        // 6. firstCheck 판별
        //   현재 view_count를 조회해서 1이면 최초 조회로 판단
        ResultView after = resultViewMapper.findByParticipantId(participant.getParticipantId());
        boolean isFirstCheck = (after != null && after.getViewCount() == 1);

        // 7. 응답 구성
        ResultResponse response = new ResultResponse();
        response.setPhoneLast4(participant.getPhoneLast4());
        response.setWon(won);
        response.setFirstCheck(isFirstCheck);

        // 정책: "첫 조회"일 때만 상세 결과(번호/등수) 공개
        if (isFirstCheck) {
            response.setResultTier(ticket.getResult().name());
            response.setResultLabel(RESULT_LABELS.get(ticket.getResult()));
            response.setLottoNumbers(List.of(
                    ticket.getNum1(), ticket.getNum2(), ticket.getNum3(),
                    ticket.getNum4(), ticket.getNum5(), ticket.getNum6()
            ));
        }

        return response;
    }

    /**
     * 참가자에게 부여 가능한 등수 후보 리스트 생성
     *
     * 정책(예시):
     * - 특정 phoneHash가 winnerPhoneHash와 일치하면 1등 후보 포함
     * - ticket_seq 구간에 따라 2등/3등 후보 포함
     * - 4등/미당첨은 기본 후보로 항상 포함
     *
     * 주의:
     * - 여기서 리턴한 후보들 중에서 NumberPool에서 실제 남아있는 슬롯(is_used=0)만 배정 가능
     */
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

        // 기본 후보 (항상 포함)
        eligible.add(PoolResult.FOURTH.name());
        eligible.add(PoolResult.NONE.name());

        return eligible;
    }
}
