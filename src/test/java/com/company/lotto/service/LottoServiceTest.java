package com.company.lotto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.company.lotto.domain.Event;
import com.company.lotto.domain.Event.EventStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class LottoServiceTest {

    @Mock private EventMapper eventMapper;
    @Mock private ParticipantMapper participantMapper;
    @Mock private NumberPoolMapper numberPoolMapper;
    @Mock private LottoTicketMapper lottoTicketMapper;
    @Mock private ResultViewMapper resultViewMapper;
    @Mock private VerificationService verificationService;

    @InjectMocks
    private LottoService lottoService;

    private Event activeEvent;
    private NumberPool sampleSlot;

    @BeforeEach
    void setUp() {
        activeEvent = new Event();
        activeEvent.setEventId(1L);
        activeEvent.setName("테스트 이벤트");
        activeEvent.setStatus(EventStatus.ACTIVE);
        activeEvent.setWinnerPhoneHash("winner-hash");
        activeEvent.setStartAt(LocalDateTime.now().minusDays(1));
        activeEvent.setEndAt(LocalDateTime.now().plusDays(1));
        activeEvent.setAnnounceStartAt(LocalDateTime.now().plusDays(2));
        activeEvent.setAnnounceEndAt(LocalDateTime.now().plusDays(5));

        sampleSlot = new NumberPool();
        sampleSlot.setPoolId(100L);
        sampleSlot.setSlot1(3);
        sampleSlot.setSlot2(7);
        sampleSlot.setSlot3(15);
        sampleSlot.setSlot4(22);
        sampleSlot.setSlot5(33);
        sampleSlot.setSlot6(45);
        sampleSlot.setResult(PoolResult.FOURTH);
        sampleSlot.setIsUsed(0);
        sampleSlot.setEventId(1L);
    }

    @Nested
    @DisplayName("participate()")
    class Participate {

        @Test
        @DisplayName("정상 참가 - 슬롯 배정 및 티켓 생성")
        void success() {
            when(verificationService.isVerified(10L)).thenReturn(true);
            when(eventMapper.findById(1L)).thenReturn(activeEvent);
            when(verificationService.hashPhone("01012345678")).thenReturn("phone-hash");
            when(participantMapper.findByPhoneHashAndEventId("phone-hash", 1L)).thenReturn(null);
            when(participantMapper.selectNextTicketSeq(1L)).thenReturn(1);
            when(verificationService.encryptPhone("01012345678")).thenReturn("encrypted");
            when(numberPoolMapper.findRandomAvailableSlot(eq(1L), anyList())).thenReturn(sampleSlot);

            ParticipateResponse response = lottoService.participate("01012345678", 1L, 10L);

            assertNotNull(response);
            assertEquals(List.of(3, 7, 15, 22, 33, 45), response.getLottoNumbers());
            assertEquals("5678", response.getPhoneLast4());

            verify(participantMapper).insertParticipant(any(Participant.class));
            verify(numberPoolMapper).markUsed(100L);
            verify(lottoTicketMapper).insertTicket(any(LottoTicket.class));
        }

        @Test
        @DisplayName("미인증 상태 - 예외 발생")
        void failWhenNotVerified() {
            when(verificationService.isVerified(10L)).thenReturn(false);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> lottoService.participate("01012345678", 1L, 10L));
            assertEquals("인증이 완료되지 않았습니다.", ex.getMessage());
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 - 예외 발생")
        void failWhenEventNotFound() {
            when(verificationService.isVerified(10L)).thenReturn(true);
            when(eventMapper.findById(999L)).thenReturn(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> lottoService.participate("01012345678", 999L, 10L));
            assertEquals("존재하지 않는 이벤트입니다.", ex.getMessage());
        }

        @Test
        @DisplayName("비활성 이벤트 - 예외 발생")
        void failWhenEventNotActive() {
            activeEvent.setStatus(EventStatus.ENDED);
            when(verificationService.isVerified(10L)).thenReturn(true);
            when(eventMapper.findById(1L)).thenReturn(activeEvent);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> lottoService.participate("01012345678", 1L, 10L));
            assertEquals("진행 중인 이벤트가 아닙니다.", ex.getMessage());
        }

        @Test
        @DisplayName("중복 참가 (애플리케이션 레벨) - 예외 발생")
        void failWhenDuplicate() {
            when(verificationService.isVerified(10L)).thenReturn(true);
            when(eventMapper.findById(1L)).thenReturn(activeEvent);
            when(verificationService.hashPhone("01012345678")).thenReturn("phone-hash");
            when(participantMapper.findByPhoneHashAndEventId("phone-hash", 1L))
                    .thenReturn(new Participant());

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> lottoService.participate("01012345678", 1L, 10L));
            assertEquals("이미 참가한 번호입니다.", ex.getMessage());
        }

        @Test
        @DisplayName("중복 참가 (DB 레벨 DuplicateKeyException) - 예외 변환")
        void failWhenDuplicateKeyAtDbLevel() {
            when(verificationService.isVerified(10L)).thenReturn(true);
            when(eventMapper.findById(1L)).thenReturn(activeEvent);
            when(verificationService.hashPhone("01012345678")).thenReturn("phone-hash");
            when(participantMapper.findByPhoneHashAndEventId("phone-hash", 1L)).thenReturn(null);
            when(participantMapper.selectNextTicketSeq(1L)).thenReturn(1);
            when(verificationService.encryptPhone("01012345678")).thenReturn("encrypted");
            doThrow(new DuplicateKeyException("Duplicate entry"))
                    .when(participantMapper).insertParticipant(any());

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> lottoService.participate("01012345678", 1L, 10L));
            assertEquals("이미 참가한 번호입니다.", ex.getMessage());
        }

        @Test
        @DisplayName("슬롯 소진 시 예외 발생")
        void failWhenNoSlotAvailable() {
            when(verificationService.isVerified(10L)).thenReturn(true);
            when(eventMapper.findById(1L)).thenReturn(activeEvent);
            when(verificationService.hashPhone("01012345678")).thenReturn("phone-hash");
            when(participantMapper.findByPhoneHashAndEventId("phone-hash", 1L)).thenReturn(null);
            when(participantMapper.selectNextTicketSeq(1L)).thenReturn(1);
            when(verificationService.encryptPhone("01012345678")).thenReturn("encrypted");
            when(numberPoolMapper.findRandomAvailableSlot(eq(1L), anyList())).thenReturn(null);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> lottoService.participate("01012345678", 1L, 10L));
            assertEquals("배정 가능한 슬롯이 없습니다.", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("getEligibleResults() - 자격 범위 결정")
    class GetEligibleResults {

        @Test
        @DisplayName("1등 당첨자 - FIRST 포함")
        void winnerIncludesFirst() {
            List<String> results = lottoService.getEligibleResults("winner-hash", activeEvent, 500);
            assertTrue(results.contains("FIRST"));
            assertTrue(results.contains("FOURTH"));
            assertTrue(results.contains("NONE"));
            assertFalse(results.contains("SECOND"));
            assertFalse(results.contains("THIRD"));
        }

        @Test
        @DisplayName("ticketSeq 1~999 - FOURTH, NONE")
        void range1to999() {
            List<String> results = lottoService.getEligibleResults("phone-hash", activeEvent, 500);
            assertEquals(List.of("FOURTH", "NONE"), results);
        }

        @Test
        @DisplayName("ticketSeq 1000~1999 - THIRD, FOURTH, NONE")
        void range1000to1999() {
            List<String> results = lottoService.getEligibleResults("phone-hash", activeEvent, 1500);
            assertEquals(List.of("THIRD", "FOURTH", "NONE"), results);
        }

        @Test
        @DisplayName("ticketSeq 2000~7000 - SECOND, THIRD, FOURTH, NONE")
        void range2000to7000() {
            List<String> results = lottoService.getEligibleResults("phone-hash", activeEvent, 3000);
            assertEquals(List.of("SECOND", "THIRD", "FOURTH", "NONE"), results);
        }

        @Test
        @DisplayName("ticketSeq 7001~8000 - THIRD, FOURTH, NONE")
        void range7001to8000() {
            List<String> results = lottoService.getEligibleResults("phone-hash", activeEvent, 7500);
            assertEquals(List.of("THIRD", "FOURTH", "NONE"), results);
        }

        @Test
        @DisplayName("ticketSeq 8001+ - FOURTH, NONE")
        void range8001plus() {
            List<String> results = lottoService.getEligibleResults("phone-hash", activeEvent, 9000);
            assertEquals(List.of("FOURTH", "NONE"), results);
        }

        @Test
        @DisplayName("1등 당첨자 + ticketSeq 3000 - FIRST, SECOND, THIRD, FOURTH, NONE")
        void winnerInMiddleRange() {
            List<String> results = lottoService.getEligibleResults("winner-hash", activeEvent, 3000);
            assertEquals(List.of("FIRST", "SECOND", "THIRD", "FOURTH", "NONE"), results);
        }
    }

    @Nested
    @DisplayName("checkResult()")
    class CheckResult {

        @Test
        @DisplayName("정상 결과 조회 - 첫 번째 확인")
        void firstCheck() {
            Event announcingEvent = new Event();
            announcingEvent.setEventId(1L);
            announcingEvent.setAnnounceStartAt(LocalDateTime.now().minusHours(1));
            announcingEvent.setAnnounceEndAt(LocalDateTime.now().plusHours(1));

            Participant participant = new Participant();
            participant.setParticipantId(50L);
            participant.setPhoneLast4("5678");

            LottoTicket ticket = new LottoTicket();
            ticket.setNum1(3); ticket.setNum2(7); ticket.setNum3(15);
            ticket.setNum4(22); ticket.setNum5(33); ticket.setNum6(45);
            ticket.setResult(PoolResult.SECOND);

            when(eventMapper.findById(1L)).thenReturn(announcingEvent);
            when(verificationService.hashPhone("01012345678")).thenReturn("phone-hash");
            when(participantMapper.findByPhoneHashAndEventId("phone-hash", 1L)).thenReturn(participant);
            when(lottoTicketMapper.findByParticipantId(50L)).thenReturn(ticket);
            when(resultViewMapper.findByParticipantId(50L)).thenReturn(null);

            ResultResponse response = lottoService.checkResult("01012345678", 1L);

            assertTrue(response.isWon());
            assertTrue(response.isFirstCheck());
            assertEquals("SECOND", response.getResultTier());
            assertEquals("2등 당첨", response.getResultLabel());
            assertEquals(List.of(3, 7, 15, 22, 33, 45), response.getLottoNumbers());
            verify(resultViewMapper).insert(any(ResultView.class));
        }

        @Test
        @DisplayName("재조회 시 viewCount 증가")
        void subsequentCheck() {
            Event announcingEvent = new Event();
            announcingEvent.setEventId(1L);
            announcingEvent.setAnnounceStartAt(LocalDateTime.now().minusHours(1));
            announcingEvent.setAnnounceEndAt(LocalDateTime.now().plusHours(1));

            Participant participant = new Participant();
            participant.setParticipantId(50L);
            participant.setPhoneLast4("5678");

            LottoTicket ticket = new LottoTicket();
            ticket.setResult(PoolResult.NONE);
            ticket.setNum1(1); ticket.setNum2(2); ticket.setNum3(3);
            ticket.setNum4(4); ticket.setNum5(5); ticket.setNum6(6);

            when(eventMapper.findById(1L)).thenReturn(announcingEvent);
            when(verificationService.hashPhone("01012345678")).thenReturn("phone-hash");
            when(participantMapper.findByPhoneHashAndEventId("phone-hash", 1L)).thenReturn(participant);
            when(lottoTicketMapper.findByParticipantId(50L)).thenReturn(ticket);
            when(resultViewMapper.findByParticipantId(50L)).thenReturn(new ResultView());

            ResultResponse response = lottoService.checkResult("01012345678", 1L);

            assertFalse(response.isWon());
            assertFalse(response.isFirstCheck());
            verify(resultViewMapper).incrementViewCount(50L);
        }

        @Test
        @DisplayName("발표 기간 이전 - 예외 발생")
        void failBeforeAnnouncement() {
            Event futureEvent = new Event();
            futureEvent.setEventId(1L);
            futureEvent.setAnnounceStartAt(LocalDateTime.now().plusDays(1));
            futureEvent.setAnnounceEndAt(LocalDateTime.now().plusDays(5));

            when(eventMapper.findById(1L)).thenReturn(futureEvent);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> lottoService.checkResult("01012345678", 1L));
            assertEquals("아직 발표 기간이 아닙니다.", ex.getMessage());
        }

        @Test
        @DisplayName("참가 이력 없음 - 예외 발생")
        void failWhenNotParticipated() {
            Event announcingEvent = new Event();
            announcingEvent.setEventId(1L);
            announcingEvent.setAnnounceStartAt(LocalDateTime.now().minusHours(1));
            announcingEvent.setAnnounceEndAt(LocalDateTime.now().plusHours(1));

            when(eventMapper.findById(1L)).thenReturn(announcingEvent);
            when(verificationService.hashPhone("01012345678")).thenReturn("phone-hash");
            when(participantMapper.findByPhoneHashAndEventId("phone-hash", 1L)).thenReturn(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> lottoService.checkResult("01012345678", 1L));
            assertEquals("참가 이력이 없습니다.", ex.getMessage());
        }
    }
}
