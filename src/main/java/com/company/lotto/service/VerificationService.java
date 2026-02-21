package com.company.lotto.service;

import com.company.lotto.config.CodeStore;
import com.company.lotto.domain.PhoneVerification;
import com.company.lotto.domain.PhoneVerification.VerificationStatus;
import com.company.lotto.repository.PhoneVerificationMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VerificationService {

    private final CodeStore codeStore;
    private final PhoneVerificationMapper phoneVerificationMapper;
    private final String phonePepper;
    private final SecretKeySpec encryptionKey;

    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final String PHONE_SALT = "lotto-event-phone-salt-2026";
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    public VerificationService(
            CodeStore codeStore,
            PhoneVerificationMapper phoneVerificationMapper,
            @Value("${phone.hash.pepper}") String phonePepper,
            @Value("${phone.encrypt.key}") String encryptKey) {
        this.codeStore = codeStore;
        this.phoneVerificationMapper = phoneVerificationMapper;
        this.phonePepper = phonePepper;
        this.encryptionKey = deriveKey(encryptKey);
    }

    private SecretKeySpec deriveKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public Map<String, Object> sendCode(String phoneNumber, Long eventId) {
        String code = generateCode();

        PhoneVerification verification = new PhoneVerification();
        verification.setEventId(eventId);
        verification.setStatus(VerificationStatus.REQUESTED);
        phoneVerificationMapper.insert(verification);

        String redisKey = "verification:" + verification.getVerificationId();
        codeStore.save(redisKey, code, CODE_TTL);

        return Map.of(
                "verificationId", verification.getVerificationId(),
                "code", code
        );
    }

    public boolean verifyCode(Long verificationId, String code) {
        String redisKey = "verification:" + verificationId;
        String storedCode = codeStore.get(redisKey);

        if (storedCode == null || !storedCode.equals(code)) {
            return false;
        }

        codeStore.delete(redisKey);
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

    public String encryptPhone(String phoneNumber) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(phoneNumber.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Phone encryption failed", e);
        }
    }

    public String decryptPhone(String encryptedPhone) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedPhone);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Phone decryption failed", e);
        }
    }

    private String normalizePhone(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }
}
