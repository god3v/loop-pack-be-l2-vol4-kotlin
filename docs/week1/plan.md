# Week 1 — User 도메인 TDD 플랜

> **입력 명세**: [requirements.md (v1.1)](./requirements.md)
> **방법론**: [Kent Beck TDD & Tidy First](../tdd-guideline.md)
> **시작일**: 2026-05-10

---

## 사용 규칙

- 한 줄 = 하나의 실패 테스트 = **하나의 Red → Green → Refactor 사이클**
- 위에서부터 순서대로 처리한다. **Outside-in** 흐름: 각 Use Case 안에서 도메인 → Repository → Facade → Controller 로 내려간다.
- 사용자가 **"go"** 하면 Claude 는 첫 `- [ ]` 항목 1개를 구현 → 사용자 확인 → 체크박스 갱신 + 커밋.
- 진척 표시: `- [ ]` 미시작 / `- [~]` 진행 중(Red 만) / `- [x]` 완료.

---

## 테스트 유형 범례

| 태그 | 의미 | 도구 | 본 프로젝트 명명 |
|---|---|---|---|
| `[단위]` | Spring 없이 순수 JUnit. 의존성은 Mock(MockK) | JUnit5 + MockK | `*Test.kt` |
| `[통합]` | `@SpringBootTest` 전체 컨텍스트 + 실 DB(Testcontainers) | + Testcontainers | `*IntegrationTest.kt` |
| `[E2E]` | `@SpringBootTest(RANDOM_PORT)` + 실 톰캣 + 실 DB + TestRestTemplate | + TestRestTemplate | `*E2ETest.kt` |

### 계층별 매핑 원칙 (Clean/Hexagonal 하이브리드)
- 도메인 로직 (Service + VO/Entity) → `[단위]` (포트는 Mock)
- Repository 구현 → `[통합]` (JPA 매핑/UNIQUE 는 실 DB)
- Application Facade → **하이브리드** — 변환/위임은 `[단위]`, 트랜잭션은 `[통합]`
- Controller → `[E2E]` (HTTP 계약은 실 톰캣)

### 예외 처리 규약
- 모든 도메인/검증 예외는 **`CoreException(ErrorType, customMessage?)`** 로 던진다.
- `IllegalArgumentException` 직접 사용 금지. ErrorType:
  - `BAD_REQUEST` (400) — 형식/길이 검증 실패, 비밀번호 RULE 위반, 동일 비번
  - `UNAUTHORIZED` (401) — 인증 실패 (헤더 누락/ID 없음/비번 불일치, 응답에서 구분 X)
  - `DUPLICATE_LOGIN_ID`, `DUPLICATE_EMAIL` (409) — 가입 시 중복

---

## 모듈/패키지 위치

- 도메인: `com.loopers.domain.user`
- 어플리케이션: `com.loopers.application.user`
- 인프라: `com.loopers.infrastructure.user`
- 인터페이스: `com.loopers.interfaces.api.user`
- DB 테이블: `users`

---

## Phase 1 — 회원가입 (Use Case)

> Outside-in. 도메인 행위(`UserService.signup()`) 에서 시작해 Repository → Facade → Controller 로 내려간다.
> 행위 한 줄로 표현하되, 한 테스트 안에서 여러 입력 케이스를 `assertAll` / parameterized 로 묶어 검증한다.

### 1.1 도메인 로직 — `UserService.signup()` `[단위]`

#### 1.1.1 정상 가입 (해피패스)

- [ ] 유효한 입력으로 회원가입 시 가입자 id 가 발급되고, 동일 loginId 로 영속 조회가 가능하다

#### 1.1.2 보안 invariant

- [ ] 회원가입 시 비밀번호는 평문으로 저장되지 않는다 (해시 형태로 저장됨)

#### 1.1.3 중복 거부

- [ ] 동일 loginId 로 가입을 시도하면 `CoreException(DUPLICATE_LOGIN_ID)` 가 발생한다
- [ ] 동일 email 로 가입을 시도하면 `CoreException(DUPLICATE_EMAIL)` 가 발생한다

#### 1.1.4 입력 검증 거부

- [ ] 형식이 잘못된 loginId 로 가입을 시도하면 `CoreException(BAD_REQUEST)` 가 발생한다 (영문/숫자 외 문자 / 4자 미만 / 21자 이상)
- [ ] 형식이 잘못된 name 으로 가입을 시도하면 `CoreException(BAD_REQUEST)` 가 발생한다 (2자 미만 / 51자 이상 / 숫자·특수문자·공백 포함)
- [ ] 형식이 잘못된 email 로 가입을 시도하면 `CoreException(BAD_REQUEST)` 가 발생한다 (`@` 없음 / TLD 부족 / 255자 초과)
- [ ] 가입 자격이 없는 birthDate 로 가입을 시도하면 `CoreException(BAD_REQUEST)` 가 발생한다 (만 14세 미만 / 미래 일자)

#### 1.1.5 비밀번호 RULE 거부

- [ ] 길이가 RULE 을 벗어난 비밀번호로 가입을 시도하면 `CoreException(BAD_REQUEST)` 가 발생한다 (8자 미만 / 17자 이상)
- [ ] 허용 문자 집합(영문/숫자/특수문자) 외 문자가 포함된 비밀번호로 가입을 시도하면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] 3개 카테고리(영문/숫자/특수문자) 중 하나라도 빠진 비밀번호로 가입을 시도하면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] 생년월일(`yyyyMMdd` 또는 `yyMMdd`) 을 포함한 비밀번호로 가입을 시도하면 `CoreException(BAD_REQUEST)` 가 발생한다

#### 1.1.6 (보조) 도메인 모델 회귀 방어 — 필요 시 추가

> 위 행위 테스트 진행 중 자연스럽게 도출된 VO/Entity 에 회귀 방어용 단위 테스트가 추가로 필요하면 본 섹션에 추가한다. 미리 적지 않는다 (Outside-in 정신).

---

### 1.2 Repository 구현 — `UserRepositoryImpl` `[통합]`

> Testcontainers MySQL + `@SpringBootTest` 전체 컨텍스트 + `DatabaseCleanUp`.
> 회원가입 use case 가 필요로 하는 메서드만 우선 구현.

- [ ] `[통합]` save 후 findById 로 동일 User 가 조회된다 (BaseEntity 의 createdAt/updatedAt 도 자동 채움)
- [ ] `[통합]` existsByLoginId 는 존재 여부를 boolean 으로 반환한다
- [ ] `[통합]` existsByEmail 는 존재 여부를 boolean 으로 반환한다
- [ ] `[통합]` findByLoginId 로 loginId 기준 조회가 가능하다 (다른 use case 에서도 필요)
- [ ] `[통합]` login_id 컬럼의 UNIQUE 제약이 동작한다 (동일 loginId 두 번 저장 시 `DataIntegrityViolationException`)
- [ ] `[통합]` email 컬럼의 UNIQUE 제약이 동작한다

---

### 1.3 Application Facade — `UserFacade.signup()` 하이브리드

- [ ] `[단위]` signup(SignupCommand) 가 정상 입력 시 SignupResult(id, loginId) 를 반환한다
- [ ] `[단위]` Command 의 문자열들이 정확한 VO (LoginId/Email/Password) 로 변환되어 Service 에 전달된다
- [ ] `[단위]` 도메인 예외(`DUPLICATE_LOGIN_ID`, `DUPLICATE_EMAIL`, `BAD_REQUEST`) 가 그대로 위로 전파된다
- [ ] `[통합]` 가입 도중 예외 발생 시 부분 저장이 없다 (트랜잭션 롤백 검증 — 실 DB)

---

### 1.4 Controller — `POST /api/v1/users` `[E2E]`

> `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` + Testcontainers MySQL.

- [ ] `[E2E]` 정상 가입 요청 시 200 + `ApiResponse.success(data = { id, loginId })` 를 반환한다
- [ ] `[E2E]` loginId / name / email 형식 위반 시 각각 400 + BAD_REQUEST 를 반환한다
- [ ] `[E2E]` 비밀번호 RULE 위반 시 400 을 반환한다
- [ ] `[E2E]` 만 14세 미만의 birthDate 면 400 을 반환한다
- [ ] `[E2E]` 중복 loginId 면 409 + `DUPLICATE_LOGIN_ID` 응답을 반환한다
- [ ] `[E2E]` 중복 email 이면 409 + `DUPLICATE_EMAIL` 응답을 반환한다
- [ ] `[E2E]` 응답 어디에도 password (평문/해시) 가 노출되지 않는다

---

## Phase 2 — 내 정보 조회 (Use Case)

> **상태**: 회원가입 완료 후 동일한 use case 중심 패턴으로 재편 예정.
> 아래는 v0 기존 항목을 보존한 형태이며, Phase 1 작업이 끝나면 행위 중심으로 재편한다.

### 2.1 도메인 로직 — 인증/조회 `[단위]`

- [ ] `[단위]` 존재하지 않는 loginId 로 식별 시도하면 `CoreException(UNAUTHORIZED)` 가 발생한다
- [ ] `[단위]` 존재하는 loginId 지만 비밀번호가 일치하지 않으면 `CoreException(UNAUTHORIZED)` 가 발생한다
- [ ] `[단위]` loginId/비번이 모두 일치하면 식별된 User 가 반환된다
- [ ] `[단위]` User.name() 은 마지막 글자를 `*` 로 치환해 반환한다 (`홍길동` → `홍길*`, `김수` → `김*`)

### 2.2 Application Facade — `UserFacade.getMyInfo()` `[단위]`

- [ ] `[단위]` 정상 인증 시 MyInfoResult 가 반환된다 (name 은 마스킹된 상태)
- [ ] `[단위]` 인증 실패 시 `CoreException(UNAUTHORIZED)` 가 전파된다
- [ ] `[단위]` MyInfoResult 에는 password 필드 자체가 존재하지 않는다 (직렬화 검증)

### 2.3 Controller — `GET /api/v1/users/me` `[E2E]`

- [ ] `[E2E]` 정상 헤더로 요청 시 200 + 마스킹된 name 포함 응답
- [ ] `[E2E]` `X-Loopers-LoginId` / `X-Loopers-LoginPw` 누락 시 401
- [ ] `[E2E]` 존재하지 않는 loginId 또는 비밀번호 불일치 시 401 (구분 없음)
- [ ] `[E2E]` 응답에 password 가 포함되지 않는다

---

## Phase 3 — 비밀번호 수정 (Use Case)

> **상태**: 회원가입 완료 후 동일한 use case 중심 패턴으로 재편 예정.

### 3.1 도메인 로직 — `UserService.changePassword()` `[단위]`

- [ ] `[단위]` 현재 비번이 일치하지 않으면 `CoreException(UNAUTHORIZED)` 가 발생한다
- [ ] `[단위]` 새 비번이 현재 비번과 동일하면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` 새 비번이 RULE 을 위반하면 `CoreException(BAD_REQUEST)` 가 발생한다
- [ ] `[단위]` 정상 변경 시 Repository 를 통해 해시가 갱신된다

### 3.2 Application Facade — `UserFacade.changePassword()` 하이브리드

- [ ] `[단위]` 헤더 LoginPw 와 body prevPw 가 모두 일치하면 Service.changePassword 가 호출된다
- [ ] `[단위]` 헤더와 body 비번이 다르면 `CoreException(UNAUTHORIZED)` 가 전파된다 (이중 검증)
- [ ] `[단위]` 도메인 예외가 그대로 전파된다
- [ ] `[통합]` 비밀번호 변경이 한 트랜잭션으로 커밋된다 (변경 후 이전 비번으로 인증 거부)

### 3.3 Controller — `PATCH /api/v1/users/me/password` `[E2E]`

- [ ] `[E2E]` 정상 요청 시 200 + `ApiResponse.success()` (data: null)
- [ ] `[E2E]` 헤더 인증 실패 / body prevPw 불일치 시 401
- [ ] `[E2E]` nextPw RULE 위반 또는 현재 비번과 동일 시 400
- [ ] `[E2E]` 변경 성공 후 이전 비번으로는 GET /me 가 401 (전 경로 일관성 검증)

---

## 진행 로그

- 2026-05-13: use case 중심으로 재편 (Outside-in TDD). Phase 1 = 회원가입, Phase 2/3 = placeholder 형태로 보존.
- 2026-05-10: 플랜 초안 작성 (requirements v1.1 기준, CoreException + 테스트 유형 매핑).
