# Lotto Event System

휴대폰 인증 기반 로또 이벤트 시스템. 이벤트 생성 시 10,000개의 로또 번호 풀을 사전 생성하고, 참가자에게 확률적으로 배정한다.

## 기술 스택

- **Java 17** / **Spring Boot 4.0.2**
- **MyBatis 4.0.1**
- **MySQL,H2**
- **Redis** (인증번호 캐싱, 3분 TTL)

## 환경 변수

| 변수 | 설명 | 기본값                |
|------|------|--------------------|
| `LOTTO_DB_URL` | MySQL JDBC URL | (필수)               |
| `LOTTO_DB_USERNAME` | DB 사용자명 | (필수)               |
| `LOTTO_DB_PASSWORD` | DB 비밀번호 | (필수)               |
| `REDIS_HOST` | Redis 호스트 | `upstash endpoint` |
| `REDIS_PORT` | Redis 포트 | `6379`             |
| `PHONE_HASH_PEPPER` | 전화번호 해시 Pepper | (필수, 운영 시 변경)      |
| `PHONE_ENCRYPT_KEY` | 전화번호 암호화 키 | (필수, 운영 시 변경)      |


서버는 `http://localhost:8080`에서 구동된다.

## 핵심 비즈니스 로직

### 참가 흐름

```
이벤트 참가 -> 폰 번호 인증 → 인증 번호 입력 → 번호 풀에서 슬롯 배정 → 로또 티켓 발급
```

1. 인증번호 발송 (Redis에 3분간 저장)
2. 인증 확인 후 참가 처리
3. ticketSeq에 따라 자격 등수 결정, 남은 슬롯 비율로 확률적 배정
4. 발표 기간에만 결과 조회 가능

### 당첨 확률 구조

이벤트당 10,000개 슬롯: 1등 1개, 2등 5개, 3등 44개, 4등 950개, 미당첨 9,000개.

ticketSeq에 따라 자격 범위가 결정되고, 해당 범위 내 남은 슬롯에서 랜덤 배정된다.

| ticketSeq | 자격 등수 |
|-----------|----------|
| 1~999 | 4등, 미당첨 |
| 1,000~1,999 | 3등, 4등, 미당첨 |
| 2,000~7,000 | 2등, 3등, 4등, 미당첨 |
| 7,001~8,000 | 3등, 4등, 미당첨 |
| 8,001~10,000 | 4등, 미당첨 |

1등은 이벤트 생성 시 지정된 전화번호 해시와 일치하는 참가자에게 배정된다.

## API

### 이벤트

| Method | Endpoint                              | 설명 |
|--------|---------------------------------------|------|
| GET | `/api/events?page=1&size=10`          | 이벤트 목록 (페이지네이션) |
| POST | `/api/events`                         | 이벤트 생성 + 번호 풀 자동 생성 |
| GET | `/api/events/{eventId}`               | 이벤트 상세 |
| GET | `/api/events/active`                  | 현재 진행 중인 이벤트 |
| GET | `/api/events/announcing`              | 현재 발표 중인 이벤트 |
| POST | `/api/events/{eventId}/generate-pool` | 번호 풀 재생성 |

### 인증

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/verification/send` | 인증번호 발송 |
| POST | `/api/verification/verify` | 인증번호 확인 |

### 참가 / 결과

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/lotto/participate` | 로또 참가 |
| POST | `/api/lotto/result` | 결과 조회 (발표 기간만) |

## 데이터 모델

```
event
 ├── number_pool       # 사전 생성된 로또 번호 풀 (순수 소모 풀)
 ├── participant        # 참가자 (휴대폰 해시/암호화)
 │    ├── lotto_ticket  # 발급된 티켓 (번호 + 결과)
 │    ├── result_view   # 결과 조회 이력
 │    └── sms_log       # SMS 발송 이력
 └── phone_verification # 휴대폰 인증 요청
```
### ERD
![lotto-erd.png](image/lotto-erd.png)


전체 DDL은 [`src/main/resources/schema.sql`](src/main/resources/schema.sql) 참고.

## 프로젝트 구조

```
src/main/java/com/company/lotto/
├── controller/
│   ├── LottoController.java          # REST API
│   └── GlobalExceptionHandler.java   # 글로벌 예외 처리
├── service/
│   ├── LottoService.java             # 참가/결과 조회 비즈니스 로직
│   ├── NumberPoolService.java        # 번호 풀 생성
│   └── VerificationService.java      # 인증/해시/암호화
├── config/                           # 설정 파일
├── scheduler/                        # 시간 스케줄러
├── repository/                       # MyBatis Mapper 인터페이스
├── domain/                           # 엔티티
└── dto/                              # 요청/응답 DTO

src/main/resources/
├── application.yaml
├── schema.sql                        # DDL
└── mapper/                           # MyBatis XML
```

## 동시성 처리

동시 참가 요청에 대해 3단계 방어를 적용한다.

| 방어 계층 | 대상 | 방법 |
|-----------|------|------|
| ticket_seq 중복 | `selectNextTicketSeq` | `SELECT MAX + 1 ... FOR UPDATE` |
| 중복 참가 | `participant` INSERT | `UNIQUE INDEX (event_id, phone_hash)` + `DuplicateKeyException` 처리 |
| 슬롯 중복 배정 | `findRandomAvailableSlot` | `SELECT ... FOR UPDATE` |

## 개인정보 보호

전화번호는 원본을 저장하지 않는다.

| 저장 형태 | 용도 | 방식 |
|-----------|------|------|
| `phone_hash` | 중복 체크, 결과 조회 | SHA-256 (Salt + Pepper) |
| `phone_encrypted` | 복호화 필요 시 | AES-GCM (랜덤 IV) |
| `phone_last4` | 화면 표시용 | 뒷 4자리 평문 |

