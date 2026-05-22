# Architecture

`loopers-kotlin-spring-template` 의 멀티 모듈 + DDD 4계층 구조 요약.

## 전체 구성

```
loopers-kotlin-spring-template
│
├── apps/                  실행 가능한 SpringBoot 앱 (BootJar 활성)
│   ├── commerce-api          REST API · JPA · Redis · springdoc · actuator
│   ├── commerce-batch        Spring Batch (Job / Step / Tasklet)
│   └── commerce-streamer     Kafka Consumer
│
├── modules/               reusable configuration (BootJar 비활성, 일반 Jar)
│   ├── jpa                   DataSourceConfig · JpaConfig · QueryDslConfig · testFixtures(MySQL Testcontainers)
│   ├── redis                 master / readonly RedisConfig · testFixtures(Redis Testcontainers)
│   └── kafka                 KafkaConfig (producer / consumer)
│
└── supports/              add-on
    ├── jackson               Jackson 직렬화 설정
    ├── logging               로깅 설정
    └── monitoring            actuator / Prometheus 연계
```

### 위계 규칙
- `apps/*` 는 다른 `apps/*` 를 의존하지 않는다.
- `modules/*` 는 도메인/구현에 의존하지 않는 reusable configuration 만 담는다.
- `supports/*` 는 부가 기능 전용 add-on.
- 컨테이너 프로젝트(`apps`, `modules`, `supports`)에는 task 가 실행되지 않는다 — 하위 모듈로 위임한다.

## 런타임 의존

```
                    ┌─ supports:jackson · logging · monitoring
commerce-api ──────┤
                    ├─ modules:jpa     ── MySQL
                    └─ modules:redis   ── Redis (master + readonly)

commerce-batch ─── modules:jpa     ── MySQL
commerce-streamer ─ modules:kafka  ── Kafka
```

## DDD 4계층 (commerce-api 기준)

```
com.loopers
├── interfaces.api.<resource>    Controller · ApiSpec · Dto
├── application.<usecase>        Facade · Command · Result (트랜잭션 경계)
├── domain.<aggregate>           Model · Service · Repository(I) · 값객체 · 도메인예외
├── infrastructure.<aggregate>   Entity · JpaRepository · RepositoryImpl
└── support.error                CoreException · ErrorType
```

### 의존 방향

```
interfaces ──▶ application ──▶ domain ◀── infrastructure
```

- `domain` 은 스프링/JPA 어노테이션을 직접 참조하지 않는 순수 Kotlin.
- `infrastructure` 가 도메인의 `Repository` 인터페이스를 구현한다 (의존 역전).
- `interfaces` 는 요청 검증 + 응답 조립만 담당, 비즈니스 로직 금지.
- `application.Facade` 가 유스케이스 진입점이며 `@Transactional` 경계를 명시적으로 잡는다.

## 현재 도메인

| 도메인 | 위치 | 비고 |
|--------|------|------|
| user | `domain.user` · `application.user` · `infrastructure.user` · `interfaces.api.user` | `Email` · `Password` 값객체, 회원가입 / 내정보 / 비밀번호 변경 |

## 프로필
`local` (기본) · `test` (테스트 강제) · `dev` · `qa` · `prd`.

- `prd` 에서는 `springdoc.api-docs.enabled=false` (Swagger 차단).
- 테스트는 `Asia/Seoul` 타임존, `spring.profiles.active=test`, `maxParallelForks=1` 강제.

## 주요 포트 (`docker/infra-compose.yml` 기준)

| 서비스 | 포트 |
|--------|------|
| MySQL | 3306 |
| Redis master / readonly | 6379 / 6380 |
| Kafka internal / host | 9092 / 19092 |
| kafka-ui | 9099 |
| Grafana | 3000 |
