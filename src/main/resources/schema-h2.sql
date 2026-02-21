-- =============================================================
-- 로또 이벤트 시스템 DDL (H2 호환)
-- =============================================================

CREATE TABLE IF NOT EXISTS event (
    event_id                BIGINT                                              NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(100)                                        NOT NULL,
    start_at                DATETIME                                            NOT NULL,
    end_at                  DATETIME                                            NOT NULL,
    announce_start_at       DATETIME                                            NULL,
    announce_end_at         DATETIME                                            NULL,
    status                  VARCHAR(10)                                         NOT NULL DEFAULT 'READY',
    winner_phone_hash       VARCHAR(255)                                        NOT NULL,
    PRIMARY KEY (event_id),
    CHECK (status IN ('READY','ACTIVE','ENDED'))
);

CREATE TABLE IF NOT EXISTS participant (
    participant_id          BIGINT                                              NOT NULL AUTO_INCREMENT,
    event_id                BIGINT                                              NOT NULL,
    phone_hash              VARCHAR(255)                                        NOT NULL,
    phone_encrypted         VARCHAR(255)                                        NOT NULL,
    phone_last4             VARCHAR(4)                                          NOT NULL,
    ticket_seq              INT                                                 NOT NULL,
    created_at              DATETIME                                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (participant_id),
    UNIQUE (event_id, phone_hash),
    UNIQUE (event_id, ticket_seq),
    CONSTRAINT fk_participant_event FOREIGN KEY (event_id) REFERENCES event (event_id)
);

CREATE TABLE IF NOT EXISTS number_pool (
    pool_id                 BIGINT                                              NOT NULL AUTO_INCREMENT,
    slot1                   INT                                                 NOT NULL,
    slot2                   INT                                                 NOT NULL,
    slot3                   INT                                                 NOT NULL,
    slot4                   INT                                                 NOT NULL,
    slot5                   INT                                                 NOT NULL,
    slot6                   INT                                                 NOT NULL,
    result                  VARCHAR(10)                                         NOT NULL,
    is_used                 TINYINT                                             NOT NULL DEFAULT 0,
    event_id                BIGINT                                              NOT NULL,
    PRIMARY KEY (pool_id),
    CHECK (result IN ('FIRST','SECOND','THIRD','FOURTH','NONE')),
    CONSTRAINT fk_pool_event FOREIGN KEY (event_id) REFERENCES event (event_id)
);

CREATE TABLE IF NOT EXISTS lotto_ticket (
    ticket_id               BIGINT                                              NOT NULL AUTO_INCREMENT,
    participant_id          BIGINT                                              NOT NULL,
    num1                    INT                                                 NOT NULL,
    num2                    INT                                                 NOT NULL,
    num3                    INT                                                 NOT NULL,
    num4                    INT                                                 NOT NULL,
    num5                    INT                                                 NOT NULL,
    num6                    INT                                                 NOT NULL,
    result                  VARCHAR(10)                                         NOT NULL,
    issued_at               DATETIME                                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ticket_id),
    CHECK (result IN ('FIRST','SECOND','THIRD','FOURTH','NONE')),
    CONSTRAINT fk_ticket_participant FOREIGN KEY (participant_id) REFERENCES participant (participant_id)
);

CREATE TABLE IF NOT EXISTS phone_verification (
    verification_id         BIGINT                                              NOT NULL AUTO_INCREMENT,
    event_id                BIGINT                                              NOT NULL,
    status                  VARCHAR(10)                                         NOT NULL DEFAULT 'REQUESTED',
    requested_at            DATETIME                                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at             DATETIME                                            NULL,
    expired_at              DATETIME                                            NULL,
    PRIMARY KEY (verification_id),
    CHECK (status IN ('REQUESTED','VERIFIED','EXPIRED')),
    CONSTRAINT fk_verification_event FOREIGN KEY (event_id) REFERENCES event (event_id)
);

CREATE TABLE IF NOT EXISTS sms_log (
    sms_id                  BIGINT                                              NOT NULL AUTO_INCREMENT,
    participant_id          BIGINT                                              NOT NULL,
    type                    VARCHAR(10)                                         NOT NULL,
    status                  VARCHAR(10)                                         NOT NULL DEFAULT 'REQUESTED',
    requested_at            DATETIME                                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at            DATETIME                                            NULL,
    PRIMARY KEY (sms_id),
    CHECK (type IN ('TICKET','REMINDER')),
    CHECK (status IN ('REQUESTED','SUCCESS','FAILED')),
    CONSTRAINT fk_sms_participant FOREIGN KEY (participant_id) REFERENCES participant (participant_id)
);

CREATE TABLE IF NOT EXISTS result_view (
    participant_id          BIGINT                                              NOT NULL,
    view_count              INT                                                 NOT NULL DEFAULT 1,
    first_view_at           DATETIME                                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_view_at            DATETIME                                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (participant_id),
    CONSTRAINT fk_result_view_participant FOREIGN KEY (participant_id) REFERENCES participant (participant_id)
);
