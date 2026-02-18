package com.company.lotto.service;

import com.company.lotto.domain.PhoneVerification;
import com.company.lotto.domain.PhoneVerification.VerificationStatus;
import com.company.lotto.repository.PhoneVerificationMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {

    private final StringRedisTemplate redisTemplate;
    private final PhoneVerificationMapper phoneVerificationMapper;
    private final SmsService smsService;
    private final String phonePepper;

    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final String PHONE_SALT = "lotto-event-phone-salt-2026";

    public VerificationService(
            StringRedisTemplate redisTemplate,
            PhoneVerificationMapper phoneVerificationMapper,
            SmsService smsService,
            @Value("${phone.hash.pepper:default-pepper-change-me}") String phonePepper) {
        this.redisTemplate = redisTemplate;
        this.phoneVerificationMapper = phoneVerificationMapper;
        this.smsService = smsService;
        this.phonePepper = phonePepper;
    }

    @Transactional
    public Map<String, Object> sendCode(String phoneNumber, Long eventId) {
        String code = generateCode();

        PhoneVerification verification = new PhoneVerification();
        verification.setEventId(eventId);
        verification.setStatus(VerificationStatus.REQUESTED);
        phoneVerificationMapper.insert(verification);

        String redisKey = "verification:" + verification.getVerificationId();
        redisTemplate.opsForValue().set(redisKey, code, CODE_TTL);

        smsService.sendVerificationCode(phoneNumber, code);

        return Map.of("verificationId", verification.getVerificationId());
    }

    public boolean verifyCode(Long verificationId, String code) {
        String redisKey = "verification:" + verificationId;
        String storedCode = redisTemplate.opsForValue().get(redisKey);

        if (storedCode == null || !storedCode.equals(code)) {
            return false;
        }

        redisTemplate.delete(redisKey);
        phoneVerificationMapper.updateStatus(verificationId, VerificationStatus.VERIFIED.name());
        return true;
    }

    public boolean isVerified(Long verificationId) {
        PhoneVerification verification = phoneVerificationMapper.findById(verificationId);
        return verification != null && verification.getStatus() == VerificationStatus.VERIFIED;
    }

    public PhoneVerification getVerification(Long verificationId) {
        return phoneVerificationMapper.findById(verificationId);
    }

    private String generateCode() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    public String hashPhone(String phoneNumber) {
        String normalized = normalizePhone(phoneNumber);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = PHONE_SALT + normalized + phonePepper;
            byte[] hash = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String normalizePhone(String phoneNumber) {
        // 숫자만 추출
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        // +82 국제번호 → 0으로 변환
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }
}
