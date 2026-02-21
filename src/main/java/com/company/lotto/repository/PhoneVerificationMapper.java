package com.company.lotto.repository;

import com.company.lotto.domain.PhoneVerification;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PhoneVerificationMapper {

    // 휴대폰 인증 요청 생성
    void insert(PhoneVerification verification);

    // 인증 요청 단건 조회
    PhoneVerification findById(Long verificationId);

    // 인증 상태 업데이트
    void updateStatus(@Param("verificationId") Long verificationId, @Param("status") String status,
                      @Param("verifiedAt") LocalDateTime verifiedAt);
}
