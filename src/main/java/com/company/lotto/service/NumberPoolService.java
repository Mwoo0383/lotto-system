package com.company.lotto.service;

import com.company.lotto.domain.Event;
import com.company.lotto.domain.NumberPool;
import com.company.lotto.domain.NumberPool.PoolResult;
import com.company.lotto.repository.EventMapper;
import com.company.lotto.repository.NumberPoolMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NumberPoolService {

    // 이벤트 1개당 생성할 전체 슬롯 개수
    private static final int TOTAL_SLOTS = 10_000;

    // 등수별 슬롯 개수 (총합이 TOTAL_SLOTS가 되도록 구성)
    private static final int FIRST_COUNT = 1;     // 1등: 1개
    private static final int SECOND_COUNT = 5;    // 2등: 5개
    private static final int THIRD_COUNT = 44;    // 3등: 44개
    private static final int FOURTH_COUNT = 950;  // 4등: 950개

    // 미당첨은 나머지 전부 (TOTAL_SLOTS에서 위 등수들을 뺀 값)
    private static final int NONE_COUNT = TOTAL_SLOTS - FIRST_COUNT - SECOND_COUNT - THIRD_COUNT - FOURTH_COUNT;

    // DB 배치 INSERT 시 한 번에 넣을 레코드 개수 (대량 삽입 성능/메모리 균형)
    private static final int BATCH_SIZE = 1_000;

    private final NumberPoolMapper numberPoolMapper;
    private final EventMapper eventMapper;

    /**
     * 이벤트 번호 풀 생성
     *
     * 목적:
     * - 이벤트 참여 시 즉석에서 번호를 생성하는 게 아니라, 미리 10,000개의 번호 조합(NumberPool)을 만들어 "재고"처럼 관리
     * - 참여자가 들어오면 이 풀에서 하나 꺼내서(is_used=0) 배정하고, 사용 처리
     *
     * 핵심 흐름:
     * 1) 이벤트 검증 (존재/상태/중복 생성 여부)
     * 2) 당첨 번호(기준 번호) 6개 생성
     * 3) 등수별 규칙에 맞는 번호 조합을 대량 생성 (중복 방지)
     * 4) 1,000개씩 batch insert
     * 5) 이벤트 상태를 ACTIVE로 변경
     */
    @Transactional
    public void generatePool(Long eventId) {
        // 1) 이벤트 존재 여부 확인
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("존재하지 않는 이벤트입니다.");
        }

        // 2) 이미 슬롯이 생성된 이벤트면 중복 생성 방지
        int existing = numberPoolMapper.countByEventId(eventId);
            if (existing > 0) return;

        // 3) 슬롯은 READY/ACTIVE 상태에서만 생성 가능
        Event.EventStatus status = event.getStatus();
            if (status != Event.EventStatus.READY && status != Event.EventStatus.ACTIVE) {
                throw new IllegalStateException("READY/ACTIVE 상태의 이벤트만 슬롯을 생성할 수 있습니다.");
            }

        // 기준이 되는 "당첨 번호" 6개 생성 (오름차순 정렬)
        List<Integer> winningNumbers = generateWinningNumbers();

        // 모든 풀(10,000개)을 메모리에 모아서 배치로 INSERT
        List<NumberPool> allPools = new ArrayList<>(TOTAL_SLOTS);

        // 번호 조합의 중복 방지를 위한 Set
        // - 키 포맷: "1-3-10-22-33-45" 같은 형태
        Set<String> uniqueKeys = new HashSet<>();

        // 1등: 1개 (당첨번호 6개 완전 일치)
        createAndAddPool(allPools, uniqueKeys, winningNumbers, winningNumbers, PoolResult.FIRST, eventId);

        // 2등: 5개 (당첨번호 중 5개 일치 + 1개는 당첨번호에 없는 숫자)
        for (int i = 0; i < SECOND_COUNT; i++) {
            createAndAddPool(
                    allPools, uniqueKeys,
                    generateSlotWithMatches(5, winningNumbers),
                    winningNumbers,
                    PoolResult.SECOND,
                    eventId
            );
        }

        // 3등: 44개 (4개 일치)
        for (int i = 0; i < THIRD_COUNT; i++) {
            createAndAddPool(
                    allPools, uniqueKeys,
                    generateSlotWithMatches(4, winningNumbers),
                    winningNumbers,
                    PoolResult.THIRD,
                    eventId
            );
        }

        // 4등: 950개 (3개 일치)
        for (int i = 0; i < FOURTH_COUNT; i++) {
            createAndAddPool(
                    allPools, uniqueKeys,
                    generateSlotWithMatches(3, winningNumbers),
                    winningNumbers,
                    PoolResult.FOURTH,
                    eventId
            );
        }

        // 미당첨: 나머지 전부 (0~2개만 일치하도록 생성)
        for (int i = 0; i < NONE_COUNT; i++) {
            createAndAddPool(
                    allPools, uniqueKeys,
                    generateNonWinningNumbers(winningNumbers),
                    winningNumbers,
                    PoolResult.NONE,
                    eventId
            );
        }

        // 4) 배치 INSERT (1,000개씩 잘라서 INSERT)
        // - 단건 insert 10,000번보다 훨씬 빠름
        for (int i = 0; i < allPools.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, allPools.size());
            numberPoolMapper.batchInsert(allPools.subList(i, end));
        }

        // 5) 슬롯 생성이 끝났으니 이벤트를 ACTIVE로 전환
        if (status == Event.EventStatus.READY) {
                eventMapper.updateStatus(eventId, Event.EventStatus.ACTIVE.name());
            }
    }

    /**
     * 기준 당첨번호 6개 생성
     * - 1~45 범위, 중복 없이 6개
     * - 오름차순 정렬해서 반환
     */
    private List<Integer> generateWinningNumbers() {
        Set<Integer> numbers = new HashSet<>();
        while (numbers.size() < 6) {
            numbers.add(ThreadLocalRandom.current().nextInt(1, 46)); // 1~45
        }
        List<Integer> sorted = new ArrayList<>(numbers);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * 당첨 번호와 matchCount개 일치하도록 번호 조합 생성
     *
     * 로직:
     * - 당첨번호 중 matchCount개를 랜덤 선택해서 유지
     * - 나머지 숫자는 "당첨번호에 없는 숫자"로 채워서
     *   의도한 일치 개수를 정확히 맞춤
     */
    private List<Integer> generateSlotWithMatches(int matchCount, List<Integer> winningNumbers) {
        // 당첨번호를 섞어서 matchCount개를 뽑기 위한 준비
        List<Integer> winning = new ArrayList<>(winningNumbers);
        Collections.shuffle(winning);

        // matchCount개는 당첨번호에서 그대로 유지
        Set<Integer> kept = new HashSet<>(winning.subList(0, matchCount));
        Set<Integer> result = new HashSet<>(kept);

        // 나머지는 당첨번호에 없는 수로 채우기
        Set<Integer> winningSet = new HashSet<>(winningNumbers);
        while (result.size() < 6) {
            int num = ThreadLocalRandom.current().nextInt(1, 46);
            if (!winningSet.contains(num)) {
                result.add(num);
            }
        }

        // 오름차순 정렬
        List<Integer> sorted = new ArrayList<>(result);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * 미당첨 번호 생성
     * - 당첨번호와의 일치 개수가 0~2개가 되도록 생성
     * - 3개 이상 일치하면 4등 이상이 될 수 있으므로 다시 생성
     */
    private List<Integer> generateNonWinningNumbers(List<Integer> winningNumbers) {
        Set<Integer> winningSet = new HashSet<>(winningNumbers);
        List<Integer> numbers;

        // 랜덤 6개 생성 후, 당첨번호와 일치 개수가 2개 이하일 때만 통과
        do {
            Set<Integer> nums = new HashSet<>();
            while (nums.size() < 6) {
                nums.add(ThreadLocalRandom.current().nextInt(1, 46));
            }
            numbers = new ArrayList<>(nums);
        } while (countMatches(numbers, winningSet) > 2);

        Collections.sort(numbers);
        return numbers;
    }

    /**
     * numbers 중 winningSet에 포함되는 숫자 개수(일치 개수) 계산
     */
    private int countMatches(List<Integer> numbers, Set<Integer> winningSet) {
        int count = 0;
        for (int num : numbers) {
            if (winningSet.contains(num)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 번호 6개 + 결과 등급 + 이벤트ID를 NumberPool 엔티티로 변환
     * - is_used = 0(미사용)으로 초기화
     */
    private NumberPool createPool(List<Integer> numbers, PoolResult result, Long eventId) {
        NumberPool pool = new NumberPool();
        pool.setSlot1(numbers.get(0));
        pool.setSlot2(numbers.get(1));
        pool.setSlot3(numbers.get(2));
        pool.setSlot4(numbers.get(3));
        pool.setSlot5(numbers.get(4));
        pool.setSlot6(numbers.get(5));
        pool.setResult(result);
        pool.setIsUsed(0);   // 아직 배정되지 않은 슬롯
        pool.setEventId(eventId);
        return pool;
    }

    /**
     * 풀 리스트에 NumberPool 추가 (중복 번호 조합 방지 포함)
     *
     * - createKey(numbers)로 번호 조합 키를 만들고,
     * - 이미 같은 키가 있으면(중복) 해당 등수 규칙에 맞게 numbers를 다시 생성
     * - 중복이 아니면 uniqueKeys에 등록하고 pools에 추가
     */
    private void createAndAddPool(List<NumberPool> pools,
                                  Set<String> uniqueKeys,
                                  List<Integer> numbers,
                                  List<Integer> winningNumbers,
                                  PoolResult result,
                                  Long eventId) {

        String key = createKey(numbers);

        // 중복이면 다시 생성 (유니크해질 때까지 반복)
        while (uniqueKeys.contains(key)) {
            if (result == PoolResult.FIRST) {
                numbers = winningNumbers;
            } else if (result == PoolResult.NONE) {
                numbers = generateNonWinningNumbers(winningNumbers);
            } else {
                int matchCount =
                        result == PoolResult.SECOND ? 5 :
                                result == PoolResult.THIRD ? 4 : 3;
                numbers = generateSlotWithMatches(matchCount, winningNumbers);
            }
            key = createKey(numbers);
        }

        uniqueKeys.add(key);
        pools.add(createPool(numbers, result, eventId));
    }

    /**
     * 번호 6개를 문자열 키로 변환
     * - Set에서 중복 체크할 때 사용
     */
    private String createKey(List<Integer> numbers) {
        return numbers.get(0) + "-" +
                numbers.get(1) + "-" +
                numbers.get(2) + "-" +
                numbers.get(3) + "-" +
                numbers.get(4) + "-" +
                numbers.get(5);
    }
}
