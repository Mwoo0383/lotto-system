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
import java.time.LocalDateTime;
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

    // 인증코드 저장소
    // - verificationId 기반 key로 code 저장/조회/삭제
    private final CodeStore codeStore;

    // 인증 요청/상태를 DB에 기록하는 MyBatis Mapper
    private final PhoneVerificationMapper phoneVerificationMapper;

    // phone hash에 추가로 섞는 비밀값(pepper) - 외부 설정에서 주입
    // - 유출되면 위험하므로 환경변수/시크릿으로 관리
    private final String phonePepper;

    // AES-GCM 암호화에 사용할 키
    // - 외부에서 들어온 문자열 키(encryptKey)를 SHA-256으로 해싱해서 32바이트 키로 파생(derive)
    private final SecretKeySpec encryptionKey;

    // 인증코드 유효시간(TTL) 3분
    private static final Duration CODE_TTL = Duration.ofMinutes(3);

    // 해시 시에 사용할 고정 salt (프로젝트 상수)
    // - PHONE_SALT(고정) + normalizedPhone + pepper(비밀) 조합으로 해시
    private static final String PHONE_SALT = "lotto-event-phone-salt-2026";

    // AES-GCM 알고리즘/파라미터
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // GCM 권장 IV 길이(12 bytes)
    private static final int GCM_TAG_LENGTH = 128; // 인증 태그 길이(비트 단위)

    public VerificationService(
            CodeStore codeStore,
            PhoneVerificationMapper phoneVerificationMapper,
            @Value("${phone.hash.pepper}") String phonePepper,
            @Value("${phone.encrypt.key}") String encryptKey) {
        this.codeStore = codeStore;
        this.phoneVerificationMapper = phoneVerificationMapper;
        this.phonePepper = phonePepper;
        // encryptKey 문자열을 그대로 쓰지 않고 SHA-256으로 파생키 생성
        this.encryptionKey = deriveKey(encryptKey);
    }

    /**
     * encryptKey(문자열)을 SHA-256으로 해싱해서 32바이트 AES 키로 파생
     * - SecretKeySpec(hash, "AES")로 AES 키로 사용
     */
    private SecretKeySpec deriveKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 인증코드 발급
     *
     * 흐름:
     * 1) 6자리 인증코드 생성
     * 2) phone_verification 테이블에 인증 요청 기록 생성 (REQUESTED + 만료시간)
     * 3) Redis(codeStore)에 verification:{id} 키로 코드 저장(TTL=3분)
     * 4) verificationId와 code 반환 (실서비스면 code는 SMS로 발송하고 응답에 포함하지 않는 게 일반적)
     */
    public Map<String, Object> sendCode(String phoneNumber, Long eventId) {
        // 6자리 숫자 인증코드 생성
        String code = generateCode();

        // DB에 인증 요청 이력 생성
        LocalDateTime now = LocalDateTime.now();
        PhoneVerification verification = new PhoneVerification();
        verification.setEventId(eventId);
        verification.setStatus(VerificationStatus.REQUESTED);
        verification.setRequestedAt(now);
        verification.setExpiredAt(now.plusMinutes(3));
        phoneVerificationMapper.insert(verification); // PK(verificationId) 생성됨

        // Redis에 코드 저장 (TTL 적용)
        String redisKey = "verification:" + verification.getVerificationId();
        codeStore.save(redisKey, code, CODE_TTL);

        // 현재는 테스트/개발 편의상 code를 응답으로 반환
        // 실제 운영에서는 code를 반환하지 않고 SMS 발송만 하고 verificationId만 내려주는 게 보통
        return Map.of(
                "verificationId", verification.getVerificationId(),
                "code", code
        );
    }

    /**
     * 인증코드 검증
     *
     * 흐름:
     * 1) Redis에서 저장된 code 조회
     * 2) 없거나 불일치하면 실패(false)
     * 3) 일치하면 Redis code 삭제(1회성) + DB 상태를 VERIFIED로 업데이트
     */
    public boolean verifyCode(Long verificationId, String code) {
        String redisKey = "verification:" + verificationId;
        String storedCode = codeStore.get(redisKey);

        // TTL 만료되었거나 코드 불일치면 실패 처리
        if (storedCode == null || !storedCode.equals(code)) {
            return false;
        }

        // 인증 성공 시: code는 1회용으로 즉시 삭제
        codeStore.delete(redisKey);

        // DB 상태 변경 + 인증 완료 시각 기록
        phoneVerificationMapper.updateStatus(
                verificationId,
                VerificationStatus.VERIFIED.name(),
                LocalDateTime.now()
        );
        return true;
    }

    /**
     * 인증이 완료되었는지 확인
     * - DB에서 verificationId 조회 후 status가 VERIFIED인지 체크
     * - participate() 같은 비즈니스 로직에서 사용
     */
    public boolean isVerified(Long verificationId) {
        PhoneVerification verification = phoneVerificationMapper.findById(verificationId);
        return verification != null && verification.getStatus() == VerificationStatus.VERIFIED;
    }

    /**
     * 인증 객체 조회(단순 조회용)
     */
    public PhoneVerification getVerification(Long verificationId) {
        return phoneVerificationMapper.findById(verificationId);
    }

    /**
     * 6자리 숫자 인증코드 생성
     * - 000000 ~ 999999 범위
     *
     * ※ 보안적으로는 Random 대신 SecureRandom 권장 (추측 난이도 ↑)
     */
    private String generateCode() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    /**
     * 전화번호 해시 생성
     *
     * 목적:
     * - DB에서 "중복 참여 체크" 등을 할 때 원문 전화번호를 저장하지 않고 비교하기 위함
     *
     * 방식:
     * - normalizePhone으로 숫자만 남기고(국가코드 82 처리) 정규화
     * - PHONE_SALT(고정) + normalized + pepper(비밀) 조합을 SHA-256 해시
     * - Hex 문자열로 반환
     */
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

    /**
     * 전화번호 암호화(AES-GCM)
     *
     * 목적:
     * - 운영상 필요할 때 원문 복원이 가능하도록 "암호화 저장"
     * - 해시와는 용도가 다름 (해시=비복원, 암호화=복원 가능)
     *
     * 구현:
     * - 매번 랜덤 IV(12 bytes) 생성
     * - AES-GCM으로 암호화
     * - [IV + ciphertext]를 붙여서 Base64로 인코딩하여 저장/전송
     */
    public String encryptPhone(String phoneNumber) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);

            // IV는 매번 랜덤 생성해야 GCM 보안이 유지됨
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            // ciphertext에는 GCM 인증태그가 포함되어 나옴(구현체에 따라)
            byte[] encrypted = cipher.doFinal(phoneNumber.getBytes(StandardCharsets.UTF_8));

            // 복호화를 위해 IV를 함께 저장: IV + encrypted
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            // DB 저장하기 쉽게 Base64로 변환
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Phone encryption failed", e);
        }
    }

    /**
     * 전화번호 복호화(AES-GCM)
     *
     * - encryptPhone에서 저장한 Base64(IV + ciphertext)를 디코딩
     * - 앞 12바이트는 IV, 나머지는 ciphertext로 분리
     * - 같은 키 + IV로 GCM 복호화
     */
    public String decryptPhone(String encryptedPhone) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedPhone);

            // 앞 12 bytes = IV, 나머지 = encrypted
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

    /**
     * 전화번호 정규화
     *
     * - 숫자만 남김 (하이픈, 공백 제거)
     * - "82"로 시작하면 한국 번호로 보고 "0"으로 치환
     *   예: 821012345678 -> 01012345678
     */
    private String normalizePhone(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }
}
