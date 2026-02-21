package com.company.lotto.config;

import java.time.Duration;

public interface CodeStore {

    void save(String key, String value, Duration ttl);

    String get(String key);

    void delete(String key);
}
