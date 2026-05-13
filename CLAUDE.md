# CLAUDE.md

이 파일은 Claude Code 가 본 저장소에서 작업할 때 따라야 할 컨텍스트와 규칙을 정의한다.

---

## 1. 프로젝트 개요

**loopers-kotlin-spring-template** — Spring + Kotlin 멀티 모듈 커머스 백엔드 템플릿이다.

- **언어/런타임**: Kotlin 2.0.20, Java 21 (Gradle toolchain)
- **프레임워크**: Spring Boot 3.4.4, Spring Cloud 2024.0.1
- **빌드**: Gradle (Kotlin DSL), KAPT, ktlint 12.1.2, Jacoco
- **인프라**: MySQL 8.0, Redis 7.0 (master + read-only replica), Kafka 3.5.1 (KRaft)
- **관측**: Spring Actuator, Prometheus, Grafana

---

## 2. 멀티 모듈 구조

```
Root
├── apps/        실행 가능한 SpringBootApplication (BootJar 활성)
│   ├── commerce-api       REST API (web + actuator + springdoc + jpa + redis)
│   ├── commerce-batch     Spring Batch (Job/Step/Tasklet + listener)
│   └── commerce-streamer  Kafka Consumer
├── modules/     reusable configuration (BootJar 비활성, 일반 Jar)
│   ├── jpa      DataSourceConfig, JpaConfig, QueryDslConfig + testFixtures
│   ├── redis    RedisConfig (master/readonly) + testFixtures
│   └── kafka    KafkaConfig
└── supports/    add-on
    ├── jackson  Jackson 직렬화 설정
    ├── logging  로깅 설정
    └── monitoring  관측 설정
```

### 모듈 위계 규칙
- `apps/*` 는 다른 `apps/*` 를 의존하지 않는다.
- `modules/*` 는 특정 도메인/구현에 의존하지 않는 reusable configuration 만 담는다.
- `supports/*` 는 부가 기능(logging, monitoring 등) 전용 add-on 이다.
- 컨테이너 프로젝트(`apps`, `modules`, `supports`)에는 task 가 실행되지 않도록 설정되어 있다.

---

## 3. 패키지 레이아웃 (commerce-api 기준 — DDD 4계층)

```
com.loopers
├── domain.<aggregate>          Model, Service, Repository(인터페이스)
├── application.<usecase>       Facade, Info(DTO)
├── infrastructure.<aggregate>  JpaRepository, RepositoryImpl
├── interfaces.api.<resource>   Controller, ApiSpec, Dto
├── interfaces.api              ApiResponse, ApiControllerAdvice
└── support.error               CoreException, ErrorType
```

- 의존 방향: `interfaces → application → domain ← infrastructure`
- `domain` 은 인프라/스프링 어노테이션을 직접 참조하지 않는다.
- `application.Facade` 가 유스케이스 진입점이며 트랜잭션 경계를 잡는다.
- `interfaces.api` 는 요청 검증 + 응답 조립만 담당하고 비즈니스 로직을 두지 않는다.

---

## 4. 개발 규칙

### 4.1 코드 스타일
- **ktlint** 가 단일 포맷터다. 커밋 전 `make init` 으로 pre-commit hook(`.githooks/pre-commit` → `./gradlew ktlintCheck`)을 활성화한다.
- `.editorconfig`: `max_line_length = 130` (단, `*Test.kt` 는 무제한).
- `INTELLIJ_IDEA` 스타일 사용. import 순서/패키지명 룰은 비활성화되어 있다.
- 후행 콤마 허용. star import 금지.

### 4.2 Kotlin 작성 원칙
- **null-safety 최우선**. `!!` 는 불가피한 경우에만 쓰고 근거를 코멘트로 남긴다.
- JPA 엔티티에 `data class` 사용을 지양한다. equals/hashCode 는 식별자 기반으로 안정적으로 구현한다.
- scope function(`let/apply/run/also`) 과다 사용으로 가독성을 해치지 않는다.
- 컬렉션 연산에서 불필요한 eager 처리/중간 리스트 생성을 피하고 필요 시 `Sequence` 또는 반복문을 쓴다.
- 도메인 예외와 인프라 예외를 분리한다.

### 4.3 Layer 별 가이드
- **Controller**: Bean Validation + 응답 조립만. 엔티티 직접 노출 금지. 에러 응답은 `@ControllerAdvice`(`ApiControllerAdvice`) + `ApiResponse` 로 일관 처리.
- **Service**: `@Transactional` 경계/전파/`readOnly`/롤백 조건을 명시적으로 설계. 외부 호출은 타임아웃/재시도/서킷브레이커 고려. 결제/주문/이벤트 발행 류는 멱등성 보장.
- **Repository (JPA/QueryDSL)**: N+1 점검, 페이징 시 fetch join 위험 인지, 인덱스 활용성 확인, 트랜잭션 밖 Lazy 로딩 금지.
- **Domain Model**: 값 객체/엔티티 경계 명확히, 불변성 유지, 비즈니스 규칙은 도메인에 둔다.

### 4.4 설정/빌드
- 의존성 버전은 `gradle.properties` 에 모은다. 신규 라이브러리 추가 시 버전을 등록한다.
- 운영에 영향을 주는 설정(타임아웃, 커넥션 풀, 로깅 레벨) 변경 시 PR 본문에 근거와 영향 범위를 적는다.
- 민감정보를 `application*.yml` 에 직접 커밋하지 않는다.

### 4.5 프로필
`local`, `test`, `dev`, `qa`, `prd` 가 정의되어 있다. `prd` 에서는 `springdoc.api-docs.enabled=false` (Swagger 차단).
기본 활성 프로필은 `local`, 테스트는 `test` 로 강제 실행된다.

---

## 5. 테스트 규칙

- **러너**: JUnit 5 (`useJUnitPlatform()`).
- **목/픽스처**: `springmockk` 우선, 필요 시 `mockito-kotlin`. 테스트 데이터는 `Instancio` 활용.
- **통합 테스트**: Testcontainers (MySQL/Redis). 각 모듈의 `testFixtures` 에 컨테이너 설정과 cleanup 유틸이 들어 있으니 재사용한다.
  - `modules:jpa` testFixtures: `MySqlTestContainersConfig`, `DatabaseCleanUp`
  - `modules:redis` testFixtures: `RedisTestContainersConfig`, `RedisCleanUp`
- **테스트 환경 고정**: `Asia/Seoul` 타임존, `spring.profiles.active=test`, `maxParallelForks=1`.
- 단위 테스트는 행위/경계값/실패 케이스를 함께 다룬다. Mock 남용으로 의미가 흐려지면 전략을 재정렬한다.
- 통합 테스트는 격리/플래키 가능성/데이터 준비·정리 전략을 명시한다.
- 커버리지는 `jacoco` 로 측정 (XML 리포트만 생성).

---

## 6. 커밋 / PR

- **커밋 단위**: 기능 단위로 분리하고, ktlint 가 통과해야 한다(pre-commit 으로 강제됨).
- **PR 리뷰**: CodeRabbit(`.coderabbit.yaml`)이 자동 리뷰한다. 톤은 한국어 '~다' 종결, 운영/장애/보안/성능/테스트 관점 위주.
  - 지적은 `왜 문제인지(운영 관점) + 수정안 + 추가 테스트` 세트로 받는다.
  - `wip`, `draft` 키워드가 PR 제목에 있으면 자동 리뷰가 스킵된다.
- **PR 본문**: 변경 목적 → 핵심 변경점 → 리스크/주의 → 테스트/검증 방법 순서로 4~8줄 요약.
- 자동 리뷰어 배정은 `.github/auto_assign.yml` 에 따른다.

---

## 7. 로컬 실행

```shell
# 1. pre-commit hook 활성화 (최초 1회)
make init

# 2. 인프라 기동 (MySQL, Redis master/readonly, Kafka, kafka-ui)
docker-compose -f ./docker/infra-compose.yml up

# 3. 모니터링 (선택)
docker-compose -f ./docker/monitoring-compose.yml up
# Grafana: http://localhost:3000 (admin/admin)

# 4. 애플리케이션 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun
```

주요 포트: MySQL `3306`, Redis master `6379` / readonly `6380`, Kafka `9092`(internal) / `19092`(host), kafka-ui `9099`, Grafana `3000`.

---

## 8. 자주 쓰는 Gradle 명령

```shell
./gradlew ktlintCheck                       # 포맷 검사
./gradlew ktlintFormat                      # 포맷 자동 수정
./gradlew :apps:commerce-api:test           # 특정 모듈 테스트
./gradlew test                              # 전체 테스트
./gradlew :apps:commerce-api:bootRun        # API 실행
./gradlew :apps:commerce-api:bootJar        # 실행 가능 jar 빌드
./gradlew jacocoTestReport                  # 커버리지 리포트
```

---

## 9. Claude 작업 시 주의

- 새 모듈을 추가할 때는 `settings.gradle.kts` 의 `include(...)` 에 등록하고, `apps`/`modules`/`supports` 중 어디에 두는지 위계 규칙을 따른다.
- 신규 도메인은 `commerce-api` 의 `domain → application → infrastructure → interfaces` 4계층 구조를 그대로 따른다.
- 라이브러리 버전은 항상 `gradle.properties` 를 통해 관리한다 — `build.gradle.kts` 안에 하드코딩하지 않는다.
- 운영 영향 설정/의존성 변경은 사용자에게 영향 범위를 먼저 확인한다.
- 테스트는 가능한 한 `testFixtures` 의 testcontainers 유틸을 재사용한다 — 새 컨테이너를 즉흥적으로 띄우지 않는다.

---

## 10. 개발 방법론 — Kent Beck TDD & Tidy First

본 프로젝트는 신규 기능 구현 시 Kent Beck 의 **TDD + Tidy First** 원칙을 따른다.
전체 가이드는 별도 문서를 참조한다.

📄 **[docs/tdd-guideline.md](./docs/tdd-guideline.md)**

핵심 요약:
- **TDD 사이클**: Red → Green → Refactor
- **Tidy First**: 구조적 변경과 행위적 변경을 같은 커밋에 절대 섞지 않는다 — 구조 변경을 항상 먼저
- **커밋 조건**: 모든 테스트 통과 + `./gradlew ktlintCheck` 통과 + 하나의 논리적 단위 + 메시지에 구조/행위 여부 명시
- **기본 흐름**: 실패 테스트 1개 → 최소 구현 → Green → (필요 시) 구조 정리 → 별도 커밋 → 반복

Claude 는 신규 기능/결함 수정 작업을 받으면 위 가이드라인 문서를 먼저 읽고, 사용자가 "go" 라고 말하면 `plan.md` 의 다음 미체크 테스트부터 진행한다.
