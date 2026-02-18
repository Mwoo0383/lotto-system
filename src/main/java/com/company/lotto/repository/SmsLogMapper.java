package com.company.lotto.repository;

import com.company.lotto.domain.SmsLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsLogMapper {

    void insertSmsLog(SmsLog smsLog);
}
