# CLAUDE.md

Claude Code 가 본 저장소에서 작업할 때 따르는 컨텍스트와 규칙. 일반 AI 에이전트 공통 가이드는 [AGENTS.md](./AGENTS.md), 아키텍처 상세는 [docs/architecture.md](./docs/architecture.md).

## 프로젝트
**loopers-kotlin-spring-template** — Spring + Kotlin 멀티 모듈 커머스 백엔드 템플릿.

- Kotlin 2.0.20 / Java 21 / Spring Boot 3.4.4
- MySQL 8 · Redis 7 (master+readonly) · Kafka 3.5 (KRaft)
- 빌드: Gradle (Kotlin DSL) · ktlint · Jacoco

## 모듈 위계
- `apps/*` 실행 가능한 SpringBoot 앱 (commerce-api / batch / streamer). 서로 의존 금지.
- `modules/*` reusable config (jpa, redis, kafka). 도메인 의존 금지.
- `supports/*` add-on (jackson, logging, monitoring).
- 라이브러리 버전은 `gradle.properties` 에만 정의 — `build.gradle.kts` 하드코딩 금지.

## 패키지 4계층 (commerce-api · DDD)
```
com.loopers
├── domain.<aggregate>          Model · Service · Repository(I) · 값객체
├── application.<usecase>       Facade · Command · Result (트랜잭션 경계)
├── infrastructure.<aggregate>  Entity · JpaRepository · RepositoryImpl
├── interfaces.api.<resource>   Controller · ApiSpec · Dto
└── support.error               CoreException · ErrorType
```
- 의존 방향: `interfaces → application → domain ← infrastructure`.
- `domain` 은 스프링/인프라 어노테이션을 직접 참조하지 않는다.
- `application.Facade` 가 트랜잭션 경계, `interfaces.api` 는 검증 + 응답 조립만.

## 개발 규칙
- **ktlint** 단일 포맷터. 커밋 전 `make init` 으로 pre-commit hook 활성화.
- **Kotlin**: null-safety 최우선, JPA 엔티티는 `data class` 지양, 도메인/인프라 예외 분리.
- **Controller**: Bean Validation + 응답 조립만, 엔티티 직접 노출 금지.
- **Service**: `@Transactional` 경계/전파/readOnly 명시, 외부 호출은 타임아웃·멱등성.
- **Repository**: N+1·fetch join·인덱스·Lazy 로딩 경계 점검.
- 민감정보를 `application*.yml` 에 직접 커밋하지 않는다.

## 테스트
- JUnit 5 + springmockk + Instancio.
- 통합 테스트는 `modules:jpa` / `modules:redis` 의 `testFixtures` Testcontainers 재사용 — 새 컨테이너 즉흥 기동 금지.
- `Asia/Seoul`, `spring.profiles.active=test`, `maxParallelForks=1` 강제.

## 커밋 / PR
- 기능 단위 커밋. ktlint 통과 필수. CodeRabbit 자동 리뷰 (`wip`/`draft` 제목이면 스킵).
- PR 본문: 변경 목적 → 핵심 변경점 → 리스크 → 검증 방법 (4~8줄).

## 작업 방법론 — Kent Beck TDD & Tidy First
신규 기능/결함 수정은 **Red → Green → Refactor** 사이클. 구조 변경과 행위 변경은 같은 커밋에 절대 섞지 않는다 — 구조 먼저, 별도 커밋. 전체 가이드: 📄 [docs/guideline/tdd-guideline.md](./docs/guideline/tdd-guideline.md). 사용자가 "go" 라고 말하면 `plan.md` 의 다음 미체크 테스트부터 진행한다.
