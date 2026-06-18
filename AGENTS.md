# AGENTS.md

이 저장소에서 작업하는 모든 AI 에이전트(Codex, Cursor, Claude Code 등)가 공유하는 컨벤션. Claude 전용 추가 지침은 [CLAUDE.md](./CLAUDE.md), 아키텍처 상세는 [docs/architecture.md](./docs/architecture.md).

## 프로젝트
**loopers-kotlin-spring-template** — Spring + Kotlin 멀티 모듈 커머스 백엔드 템플릿.

- Kotlin 2.0.20 / Java 21 / Spring Boot 3.4.4 / Gradle (Kotlin DSL)
- MySQL 8 · Redis 7 (master+readonly) · Kafka 3.5 (KRaft)

## 시작 전 읽을 것
1. [docs/architecture.md](./docs/architecture.md) — 모듈/계층 구조 한눈에 보기
2. [docs/guideline/tdd-guideline.md](./docs/guideline/tdd-guideline.md) — TDD + Tidy First 사이클
3. 현재 작업 폴더의 `plan.md` — 진행 중인 체크리스트

## 작업 원칙
- **TDD + Tidy First**: Red → Green → Refactor. 구조 변경과 행위 변경을 같은 커밋에 섞지 않는다.
- **계층 경계**: `interfaces → application ← infrastructure`, `application → domain`. `domain` 은 스프링/JPA 를 모른다.
- **트랜잭션 경계 & 조율**: `application.Facade` 가 Repository 호출과 `@Transactional` 을 단일 소유.
- **순수 도메인 헬퍼는 허용**: 정책/계산기/스펙(예: `PasswordPolicy`, `OrderPriceCalculator`) 은 무상태·인자 기반이면 `domain` 에 둔다.
- **Tell, Don't Ask**: 도메인 상태를 밖에서 묻고(`isXxx()`) `if` 로 분기하기보다, 규칙과 그 위반 예외를 도메인 객체의 행위 메서드로 캡슐화한다. (예: `if (coupon.isExpired(now)) throw …` → `coupon.ensureIssuable(now)`)
- **null-safety 최우선**. `!!` 는 근거 코멘트와 함께만.
- **라이브러리 버전**은 `gradle.properties` 단일 출처.

## 도구
- 포맷: `./gradlew ktlintCheck` / `./gradlew ktlintFormat` — 커밋 전 통과 필수.
- 테스트: `./gradlew test` 또는 `./gradlew :apps:commerce-api:test`. 통합 테스트는 `modules:jpa` / `modules:redis` 의 `testFixtures` Testcontainers 재사용.
- 실행: `./gradlew :apps:commerce-api:bootRun` (사전에 `docker-compose -f ./docker/infra-compose.yml up`).
- 커버리지: `./gradlew jacocoTestReport`.

## 커밋 / PR
- 한 커밋은 하나의 논리적 단위. 커밋 메시지에 구조/행위 변경 여부 명시.
- PR 본문: 변경 목적 → 핵심 변경점 → 리스크 → 검증 방법 (4~8줄).
- CodeRabbit 자동 리뷰. `wip`/`draft` 키워드가 제목에 있으면 스킵.
- 민감정보(`.env`, 자격증명, `application*.yml` 내 비밀)는 절대 커밋하지 않는다.

## 자주 쓰는 패턴
- 신규 도메인 추가: 4계층을 추가한다.
  - `domain.<aggregate>` — 순수 모델/값객체/도메인 규칙, domainService, domainRepository (I)
  - `application.<usecase>` — `Facade`, `command/`, `result/`
  - `infrastructure.<aggregate>` — `Entity`, `JpaRepository`, `RepositoryImpl`
  - `interfaces.api.<resource>` — `Controller`, `ApiSpec`, `Dto`
- 신규 모듈 추가: `settings.gradle.kts` 의 `include(...)` 등록 + `apps`/`modules`/`supports` 중 적절한 위치.
- 컨테이너 프로젝트(`apps`, `modules`, `supports`) 자체에는 task 가 실행되지 않는다 — 하위 모듈을 명시한다.

## 절대 금지
- `apps/*` 끼리의 직접 의존.
- `domain` 에서 스프링/JPA 어노테이션 참조.
- `build.gradle.kts` 안에 라이브러리 버전 하드코딩.
- 테스트마다 새 Testcontainer 즉흥 기동 (testFixtures 재사용).
- 운영 영향 설정(타임아웃, 커넥션 풀, 로깅 레벨) 변경을 PR 본문에 근거 없이 포함.
