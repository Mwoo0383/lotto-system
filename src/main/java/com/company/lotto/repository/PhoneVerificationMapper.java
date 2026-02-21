package com.company.lotto.repository;

import com.company.lotto.domain.PhoneVerification;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PhoneVerificationMapper {

    void insert(PhoneVerification verification);

    PhoneVerification findById(Long verificationId);

    void updateStatus(@Param("verificationId") Long verificationId, @Param("status") String status,
                      @Param("verifiedAt") LocalDateTime verifiedAt);
}
