# Week 1 — User 도메인 TDD 플랜

> **입력 명세**: [requirements.md (v1.1)](./requirements.md)
> **방법론**: [Kent Beck TDD & Tidy First](../tdd-guideline.md)
> **시작일**: 2026-05-10

---

## 사용 규칙

- 한 줄 = 하나의 실패 테스트 = **하나의 Red → Green → Refactor 사이클**
- 위에서부터 순서대로 처리한다 (Phase 1 → 5).
- 사용자가 **"go"** 하면 Claude 는 첫 `- [ ]` 항목 1개를 구현 → 사용자 확인 → 체크박스 갱신 + 커밋.
- 진척 표시: `- [ ]` 미시작 / `- [~]` 진행 중(Red 만) / `- [x]` 완료.

---

## 테스트 유형 범례 (Clean/Hexagonal 정합 — 하이브리드)

| 태그 | 의미 | 도구 | 본 프로젝트 명명 |
|---|---|---|---|
| `[단위]` | Spring 없이 순수 JUnit. 의존성은 Mock(springmockk) 으로 차단 | JUnit5 + MockK | `*Test.kt` (예: `UserServiceTest`) |
| `[통합]` | `@SpringBootTest` 전체 컨텍스트 + 실 DB(Testcontainers) | + Testcontainers | `*IntegrationTest.kt` |
| `[E2E]` | `@SpringBootTest(RANDOM_PORT)` + 실 톰캣 + 실 DB + TestRestTemplate | + TestRestTemplate | `*E2ETest.kt` |

### 계층별 매핑 원칙
- **Phase 1 (도메인 모델)**: 전부 `[단위]` — 포트조차 없는 순수 도메인, Spring 불필요
- **Phase 2 (Domain Service)**: 전부 `[단위]` — Repository 인터페이스를 Mock 으로 끊고 비즈니스 규칙만 검증 (헥사고날 정합)
- **Phase 3 (Repository 구현)**: 전부 `[통합]` — JPA 매핑/UNIQUE 제약은 실 DB 로만 검증 가능
- **Phase 4 (Application Facade)**: **하이브리드** — 변환/조립/위임은 `[단위]`, 트랜잭션 동작은 `[통합]` 1~2개로 보강
- **Phase 5 (Controller)**: 전부 `[E2E]` — HTTP 계약/헤더/Advice 변환은 실 톰캣으로만 검증

### 예외 처리 규약
- 모든 도메인/검증 예외는 **`CoreException(ErrorType, customMessage?)`** 로 던진다.
- `IllegalArgumentException` 직접 사용 금지. ErrorType 매핑:
  - `BAD_REQUEST` (400) — 값 객체/엔티티 검증 실패, 비밀번호 RULE 위반, 동일 비번
  - `UNAUTHORIZED` (401) — 인증 실패 (헤더 누락/ID 없음/비번 불일치, 구분 없음)
  - `DUPLICATE_LOGIN_ID`, `DUPLICATE_EMAIL` (409) — 가입 시 중복

---

## 모듈/패키지 위치 (요구사항 v1.1 §12 정렬)

- 도메인: `com.loopers.domain.user`
- 어플리케이션: `com.loopers.application.user`
- 인프라: `com.loopers.infrastructure.user`
- 인터페이스: `com.loopers.interfaces.api.user`
- DB 테이블: `users`

---

## Phase 1 — 도메인 모델 (값 객체 + 엔티티) `[단위]`

> 모든 항목 `[단위]`. 스프링/JPA 의존 없이 순수 Kotlin 로 작성. 모든 검증을 생성 시점에 끝낸다.

### 1.1 LoginId 값 객체

- [ ] `[단위]` LoginId 생성 시 빈 문자열이면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` LoginId 가 4자 미만이면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` LoginId 가 21자 이상이면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` LoginId 에 영문/숫자 외 문자(한글, 특수문자, 공백)가 포함되면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` 4~20자 영문/숫자 조합으로 LoginId 생성 시 정상 인스턴스가 만들어진다
- [ ] `[단위]` 동일한 값으로 생성된 LoginId 두 인스턴스는 동등(equals)하다

### 1.2 Email 값 객체

- [ ] `[단위]` Email 생성 시 빈 문자열이면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` Email 에 `@` 가 없으면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` 로컬파트(`@` 앞부분)가 비어있으면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` 도메인파트(`@` 뒷부분)에 점(`.`) 이 없으면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` TLD 가 1자 미만이면 `CoreException(BAD_REQUEST)` 가 발생한다 (예: `a@b.`)
- [ ] `[단위]` Email 이 255자를 초과하면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` 정상 형식의 이메일로 생성 시 정상 인스턴스가 만들어진다
- [ ] `[단위]` 동일한 값으로 생성된 Email 두 인스턴스는 동등하다

### 1.3 Password 값 객체 (평문 검증용)

- [ ] `[단위]` Password 생성 시 빈 문자열이면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` Password 가 8자 미만이면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` Password 가 17자 이상이면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` Password 에 영문이 하나도 없으면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` Password 에 숫자가 하나도 없으면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` Password 에 특수문자가 하나도 없으면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` Password 에 허용되지 않은 문자(한글, 공백, 제어문자)가 포함되면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` 8~16자, 영문/숫자/특수문자를 모두 포함하면 정상 인스턴스가 만들어진다
- [ ] `[단위]` Password 가 생년월일(`yyyyMMdd`) 을 부분 문자열로 포함하면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` Password 가 생년월일(`yyMMdd`) 을 부분 문자열로 포함하면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` 생년월일과 무관한 정상 비밀번호는 생성에 성공한다
- [ ] `[단위]` Password.encode(encoder) 는 BCrypt 해시 문자열을 반환한다
- [ ] `[단위]` Password.matches(hash, encoder) 는 평문이 해시와 일치하는지 boolean 으로 반환한다

### 1.4 User 엔티티

- [ ] `[단위]` 모든 VO 와 birthDate(만 14세 이상, 과거 일자)로 User 생성 시 정상 인스턴스가 만들어진다
- [ ] `[단위]` User 생성 시 만 14세 미만의 birthDate 면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` User 생성 시 미래 birthDate 면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` User 생성 시 name 이 2자 미만이면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` User 생성 시 name 이 51자 이상이면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` User 생성 시 name 에 숫자/특수문자/공백이 포함되면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` User.maskedName() 은 이름의 마지막 글자를 `*` 로 치환해 반환한다 (`홍길동` → `홍길*`)
- [ ] `[단위]` User.maskedName() 은 2글자 이름도 마지막 글자만 마스킹한다 (`김수` → `김*`)
- [ ] `[단위]` User.changePassword(currentPlain, newPassword) 는 현재 비번이 일치하지 않으면 `CoreException(UNAUTHORIZED)` 가 발생한다
- [ ] `[단위]` User.changePassword 는 새 비번이 현재 비번과 동일하면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` User.changePassword 가 정상이면 저장된 해시가 갱신된다 (이전 해시와 다름)

---

## Phase 2 — Domain Service (`UserService`) `[단위]`

> Repository 인터페이스를 MockK 로 대체. 비즈니스 규칙만 격리 검증 (헥사고날 정합).

### 2.1 회원가입 (signup)

- [ ] `[단위]` 동일한 loginId 가 이미 존재하면 `CoreException(DUPLICATE_LOGIN_ID)` 가 발생한다
- [ ] `[단위]` 동일한 email 이 이미 존재하면 `CoreException(DUPLICATE_EMAIL)` 가 발생한다
- [ ] `[단위]` loginId/email 중복이 없으면 Repository.save 가 호출되고 발급된 id 가 반환된다
- [ ] `[단위]` 가입 시 User 가 저장될 때 password 는 해시 형태(평문 아님) 로 전달된다

### 2.2 인증 (식별)

- [ ] `[단위]` 존재하지 않는 loginId 로 식별 시도하면 `CoreException(UNAUTHORIZED)` 가 발생한다
- [ ] `[단위]` 존재하는 loginId 지만 비밀번호가 일치하지 않으면 `CoreException(UNAUTHORIZED)` 가 발생한다
- [ ] `[단위]` loginId 와 비밀번호가 모두 일치하면 식별된 User 가 반환된다

### 2.3 비밀번호 수정

- [ ] `[단위]` body 의 currentPassword 가 저장된 해시와 일치하지 않으면 `CoreException(UNAUTHORIZED)` 가 발생한다
- [ ] `[단위]` newPassword 가 현재 비번과 동일하면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` 모든 조건 충족 시 Repository 를 통해 저장된 비밀번호 해시가 갱신된다

---

## Phase 3 — Repository 구현 (`UserRepositoryImpl`) `[통합]`

> 모든 항목 `[통합]`. Testcontainers MySQL 사용 (`modules:jpa` testFixtures 재사용).
> `@SpringBootTest` 전체 컨텍스트 + `DatabaseCleanUp` 으로 격리.

### 3.1 JPA 엔티티 매핑

- [ ] `[통합]` UserJpaEntity 가 `users` 테이블에 매핑되어 저장/조회된다
- [ ] `[통합]` BaseEntity 의 createdAt/updatedAt 이 자동으로 채워진다
- [ ] `[통합]` login_id 컬럼에 UNIQUE 제약이 적용되어 있다 (동일 loginId 두 번 저장 시 예외)
- [ ] `[통합]` email 컬럼에 UNIQUE 제약이 적용되어 있다 (동일 email 두 번 저장 시 예외)

### 3.2 Repository 구현

- [ ] `[통합]` save 후 findById 로 동일한 User 가 조회된다
- [ ] `[통합]` findByLoginId 로 loginId 기준 조회가 가능하다
- [ ] `[통합]` findByEmail 로 email 기준 조회가 가능하다
- [ ] `[통합]` existsByLoginId 는 존재 여부를 boolean 으로 반환한다
- [ ] `[통합]` existsByEmail 는 존재 여부를 boolean 으로 반환한다
- [ ] `[통합]` 동일한 loginId 로 save 시 `DataIntegrityViolationException` 이 발생한다
- [ ] `[통합]` 동일한 email 로 save 시 `DataIntegrityViolationException` 이 발생한다

---

## Phase 4 — Application Facade (`UserFacade`) — 하이브리드

> **대부분 `[단위]`** (Service Mock 으로 끊고 변환/조립/위임만 격리 검증).
> **트랜잭션 동작은 `[통합]` 2개로 보강** — Mock 으로는 검증 불가능한 영역.

### 4.1 회원가입 Facade

- [ ] `[단위]` signup(SignupCommand) 가 정상 입력 시 SignupResult(id, loginId) 를 반환한다
- [ ] `[단위]` Command 의 문자열들이 정확한 VO (LoginId, Email, Password) 로 변환되어 Service 에 전달된다
- [ ] `[단위]` 도메인 예외(`DUPLICATE_LOGIN_ID`, `DUPLICATE_EMAIL`) 가 그대로 위로 전파된다 (Facade 가 삼키지 않음)
- [ ] `[통합]` 가입 도중 예외 발생 시 부분 저장이 없다 (트랜잭션 롤백 검증 — 실제 DB 로만 검증 가능)

### 4.2 내 정보 조회 Facade

- [ ] `[단위]` 헤더 인증 정보(loginId, plainPw) 가 일치하면 MyInfoResult 를 반환한다
- [ ] `[단위]` MyInfoResult 의 name 은 마지막 글자가 마스킹된 상태로 반환된다 (`홍길동` → `홍길*`)
- [ ] `[단위]` MyInfoResult 에는 password 필드 자체가 존재하지 않는다 (컴파일/직렬화 검증)
- [ ] `[단위]` 인증 실패 시 `CoreException(UNAUTHORIZED)` 가 전파된다

### 4.3 비밀번호 수정 Facade

- [ ] `[단위]` 헤더 인증 + body currentPassword 가 모두 일치하면 Service.changePassword 가 호출된다
- [ ] `[단위]` 헤더 LoginPw 와 body currentPassword 가 다르면 `CoreException(UNAUTHORIZED)` 가 전파된다 (이중 검증)
- [ ] `[단위]` 도메인 예외(RULE 위반/동일 비번) 가 그대로 전파된다
- [ ] `[통합]` 비밀번호 변경이 한 트랜잭션으로 커밋된다 (변경 후 새 해시로만 인증되고 이전 해시는 거부됨 — 실 DB)

---

## Phase 5 — Controller E2E `[E2E]`

> 모든 항목 `[E2E]`. `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` + Testcontainers MySQL.
> HTTP 계약, 헤더 처리, `ApiResponse` 포맷, `ApiControllerAdvice` 의 ErrorType → HTTP 변환을 일관 검증.

### 5.1 POST /api/v1/users — 회원가입

- [ ] `[E2E]` 정상 가입 요청 시 200 + `ApiResponse.success(data = { id, loginId })` 를 반환한다
- [ ] `[E2E]` loginId 형식 위반 시 400 + `BAD_REQUEST` 표준 에러 응답을 반환한다
- [ ] `[E2E]` name 이 1글자면 400 을 반환한다
- [ ] `[E2E]` email 형식 위반 시 400 을 반환한다
- [ ] `[E2E]` 비밀번호 RULE 위반(길이/카테고리/생년월일 포함) 시 400 을 반환한다
- [ ] `[E2E]` 만 14세 미만의 birthDate 면 400 을 반환한다
- [ ] `[E2E]` 중복 loginId 면 409 + `DUPLICATE_LOGIN_ID` 응답을 반환한다
- [ ] `[E2E]` 중복 email 이면 409 + `DUPLICATE_EMAIL` 응답을 반환한다
- [ ] `[E2E]` 응답 어디에도 password (평문/해시) 가 노출되지 않는다

### 5.2 GET /api/v1/users/me — 내 정보 조회

- [ ] `[E2E]` 정상 헤더로 요청 시 200 + 마스킹된 name 포함 응답을 반환한다
- [ ] `[E2E]` `X-Loopers-LoginId` 헤더 누락 시 401 을 반환한다
- [ ] `[E2E]` `X-Loopers-LoginPw` 헤더 누락 시 401 을 반환한다
- [ ] `[E2E]` 존재하지 않는 loginId 면 401 을 반환한다 (NOT_FOUND 가 아니라 UNAUTHORIZED — ID 노출 방지)
- [ ] `[E2E]` 비밀번호 불일치 시 401 을 반환한다
- [ ] `[E2E]` 응답에 password 가 포함되지 않는다

### 5.3 PATCH /api/v1/users/me/password — 비밀번호 수정

- [ ] `[E2E]` 정상 요청 시 200 + `ApiResponse.success()` (data: null) 를 반환한다
- [ ] `[E2E]` 헤더 인증 실패 시 401 을 반환한다
- [ ] `[E2E]` body 의 currentPassword 가 헤더 비번과 다르면 401 을 반환한다 (이중 검증)
- [ ] `[E2E]` body 의 currentPassword 가 저장된 비번과 다르면 401 을 반환한다
- [ ] `[E2E]` newPassword 가 RULE 위반이면 400 을 반환한다
- [ ] `[E2E]` newPassword 가 현재 비번과 동일하면 400 을 반환한다
- [ ] `[E2E]` 변경 성공 후 이전 비번으로는 GET /me 가 401 을 반환한다 (전 경로 일관성 검증)

### 5.4 인증 헤더 처리 인프라 (필요 시)

- [ ] `[E2E]` `HandlerMethodArgumentResolver` 또는 `@RequestHeader` 로 헤더를 받아 Facade 에 전달하는 흐름이 동작한다
  - 구현 방식 선택은 5.2 진행 중 결정 (단순 `@RequestHeader` 로 시작, 중복 발생 시 Resolver 도입)

---

## 진행 로그

- 2026-05-10: 플랜 작성 (requirements v1.1 기준, CoreException + 테스트 유형 매핑 반영)
