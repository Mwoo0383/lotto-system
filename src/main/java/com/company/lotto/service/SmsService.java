package com.company.lotto.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsService {

    private DefaultMessageService messageService;
    private final String fromNumber;
    private final String apiKey;
    private final String apiSecret;

    public SmsService(
            @Value("${coolsms.api-key}") String apiKey,
            @Value("${coolsms.api-secret}") String apiSecret,
            @Value("${coolsms.from-number}") String fromNumber) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.fromNumber = fromNumber;
    }

    private DefaultMessageService getMessageService() {
        if (messageService == null) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("CoolSMS API 키가 설정되지 않았습니다.");
            }
            messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
        }
        return messageService;
    }

    public void sendVerificationCode(String toNumber, String code) {
        Message message = new Message();
        message.setFrom(fromNumber);
        message.setTo(toNumber);
        message.setText("[로또 이벤트] 인증번호: " + code + " (3분 내 입력해주세요)");
        getMessageService().sendOne(new SingleMessageSendingRequest(message));
    }

    public void sendLottoNumbers(String toNumber, List<Integer> numbers, String phoneLast4) {
        String numbersStr = numbers.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        Message message = new Message();
        message.setFrom(fromNumber);
        message.setTo(toNumber);
        message.setText("[로또 이벤트] " + phoneLast4 + "님의 로또 번호: " + numbersStr + " 당첨 발표일에 확인해주세요!");
        getMessageService().sendOne(new SingleMessageSendingRequest(message));
    }
}
