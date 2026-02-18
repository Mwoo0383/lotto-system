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

    private static final int TOTAL_SLOTS = 10_000;
    private static final int FIRST_COUNT = 1;
    private static final int SECOND_COUNT = 5;
    private static final int THIRD_COUNT = 44;
    private static final int FOURTH_COUNT = 950;
    private static final int NONE_COUNT = TOTAL_SLOTS - FIRST_COUNT - SECOND_COUNT - THIRD_COUNT - FOURTH_COUNT;
    private static final int BATCH_SIZE = 1_000;

    private final NumberPoolMapper numberPoolMapper;
    private final EventMapper eventMapper;

    @Transactional
    public void generatePool(Long eventId) {
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new IllegalArgumentException("존재하지 않는 이벤트입니다.");
        }
        if (event.getStatus() != Event.EventStatus.READY) {
            throw new IllegalStateException("준비중 상태의 이벤트만 슬롯을 생성할 수 있습니다.");
        }
        if (numberPoolMapper.countByEventId(eventId) > 0) {
            throw new IllegalStateException("이미 슬롯이 생성된 이벤트입니다.");
        }

        List<Integer> winningNumbers = generateWinningNumbers();
        List<NumberPool> allPools = new ArrayList<>(TOTAL_SLOTS);

        // 중복 방지 set
        Set<String> uniqueKeys = new HashSet<>();

        // 1등: 1개 (6자리 일치)
        createAndAddPool(allPools, uniqueKeys, winningNumbers, winningNumbers, PoolResult.FIRST, eventId);

        // 2등: 5개 (5자리 일치)
        for (int i = 0; i < SECOND_COUNT; i++) {
            createAndAddPool(allPools, uniqueKeys, generateSlotWithMatches(5, winningNumbers), winningNumbers, PoolResult.SECOND, eventId);
        }

        // 3등: 44개 (4자리 일치)
        for (int i = 0; i < THIRD_COUNT; i++) {
            createAndAddPool(allPools, uniqueKeys, generateSlotWithMatches(4, winningNumbers), winningNumbers, PoolResult.THIRD, eventId);
        }

        // 4등: 950개 (3자리 일치)
        for (int i = 0; i < FOURTH_COUNT; i++) {
            createAndAddPool(allPools, uniqueKeys, generateSlotWithMatches(3, winningNumbers), winningNumbers, PoolResult.FOURTH, eventId);
        }

        // 미당첨: 나머지 (0~2자리 일치)
        for (int i = 0; i < NONE_COUNT; i++) {
            createAndAddPool(allPools, uniqueKeys, generateNonWinningNumbers(winningNumbers), winningNumbers, PoolResult.NONE, eventId);
        }

        // 배치 INSERT (1,000개씩 분할)
        for (int i = 0; i < allPools.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, allPools.size());
            numberPoolMapper.batchInsert(allPools.subList(i, end));
        }

        eventMapper.updateStatus(eventId, Event.EventStatus.ACTIVE.name());
    }

    private List<Integer> generateWinningNumbers() {
        Set<Integer> numbers = new HashSet<>();
        while (numbers.size() < 6) {
            numbers.add(ThreadLocalRandom.current().nextInt(1, 46));
        }
        List<Integer> sorted = new ArrayList<>(numbers);
        Collections.sort(sorted);
        return sorted;
    }

    private List<Integer> generateSlotWithMatches(int matchCount, List<Integer> winningNumbers) {
        List<Integer> winning = new ArrayList<>(winningNumbers);
        Collections.shuffle(winning);

        Set<Integer> kept = new HashSet<>(winning.subList(0, matchCount));
        Set<Integer> result = new HashSet<>(kept);

        Set<Integer> winningSet = new HashSet<>(winningNumbers);
        while (result.size() < 6) {
            int num = ThreadLocalRandom.current().nextInt(1, 46);
            if (!winningSet.contains(num)) {
                result.add(num);
            }
        }

        List<Integer> sorted = new ArrayList<>(result);
        Collections.sort(sorted);
        return sorted;
    }

    private List<Integer> generateNonWinningNumbers(List<Integer> winningNumbers) {
        Set<Integer> winningSet = new HashSet<>(winningNumbers);
        List<Integer> numbers;

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

    private int countMatches(List<Integer> numbers, Set<Integer> winningSet) {
        int count = 0;
        for (int num : numbers) {
            if (winningSet.contains(num)) {
                count++;
            }
        }
        return count;
    }

    private NumberPool createPool(List<Integer> numbers, PoolResult result, Long eventId) {
        NumberPool pool = new NumberPool();
        pool.setSlot1(numbers.get(0));
        pool.setSlot2(numbers.get(1));
        pool.setSlot3(numbers.get(2));
        pool.setSlot4(numbers.get(3));
        pool.setSlot5(numbers.get(4));
        pool.setSlot6(numbers.get(5));
        pool.setResult(result);
        pool.setIsUsed(0);
        pool.setEventId(eventId);
        return pool;
    }

    private void createAndAddPool(List<NumberPool> pools,
                                  Set<String> uniqueKeys,
                                  List<Integer> numbers,
                                  List<Integer> winningNumbers,
                                  PoolResult result,
                                  Long eventId) {

        String key = createKey(numbers);

        // 중복이면 다시 생성
        while (uniqueKeys.contains(key)) {
            if (result == PoolResult.FIRST) {
                numbers = generateWinningNumbers();
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

    private String createKey(List<Integer> numbers) {
        return numbers.get(0) + "-" +
                numbers.get(1) + "-" +
                numbers.get(2) + "-" +
                numbers.get(3) + "-" +
                numbers.get(4) + "-" +
                numbers.get(5);
    }
}
