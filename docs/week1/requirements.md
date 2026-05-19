# Week 1 — 사용자(User) 도메인 요구사항 명세

> **상태**: **v1.2 확정** — [docs/week1/plan.md](./plan.md) 작성의 기준 문서로 사용한다.
> **작성일**: 2026-05-10
> **최종 동기화**: 2026-05-15 — 실제 구현(`UserErrorType`, `Password`, `Email`)에 맞춰 갱신.
> **변경 이력**:
> - v1.2 (2026-05-15): 구현 정렬 — 회원가입 성공 응답을 `data: null` 로 단순화, ErrorType 을 구체 enum(`SIGNUP_BAD_REQUEST` / `PASSWORD_CHANGE_BAD_REQUEST` / `INVALID_PASSWORD`) 으로 분리, 비밀번호 RULE 위반은 `INVALID_PASSWORD` 로 통일. `email` 최대 길이 255자로 정정. 인증은 현재 평문 비교(BCrypt 해시는 후속 작업)임을 명시.
> - v1.1 (2026-05-10): `name` 최소 2자로 상향 (1글자 가입 차단), `email` 검증 규칙 구체화 (정규식 수준 + 255자 길이 제한).
> - v1.0 (2026-05-10): 13개 결정 사항 모두 확정. 도메인 명칭을 `User` 로 정렬 (한국어 호칭은 "회원" 유지).
> - v0.2 (2026-05-10): 범위 축소 — 가입/내 정보 조회/비밀번호 수정 3개로 한정. 헤더 기반 사용자 식별 도입.
> - v0.1 (2026-05-10): 최초 초안.

---

## 1. 목적 (Goal)

본 프로젝트의 첫 도메인으로 **사용자(User)** 를 구현한다 (한국어 호칭: 회원). TDD + Tidy First 사이클을 실제로 한 번 완주하는 것이 1차 목적이고, 후속 도메인이 의존할 사용자 식별 기반을 마련하는 것이 2차 목적이다.

---

## 2. 범위 (Scope)

### 2.1 In Scope (Week 1)
1. **회원가입**
2. **내 정보 조회**
3. **비밀번호 수정**

### 2.2 Out of Scope (이후 주차)
- 세션/JWT 기반 인증, 소셜 로그인
- 비밀번호 재설정 / 이메일 인증
- 회원 정보 수정 (이름/이메일/생년월일 변경)
- 회원 탈퇴
- 관리자용 회원 목록/검색

---

## 3. 사용자 식별 방식

본 주차는 실제 인증 체계 대신 **HTTP 헤더 기반의 단순 사용자 식별** 을 사용한다.

| 헤더 | 의미 |
|---|---|
| `X-Loopers-LoginId` | 로그인 ID |
| `X-Loopers-LoginPw` | 비밀번호 (현재는 **평문 비교** — `Password.matches` 가 평문 동등성 비교. BCrypt 해시 도입은 TODO) |

### 식별 절차
1. 헤더 둘 중 하나라도 없으면 `UNAUTHORIZED` (401) — Controller 의 `requireAuthHeaders` 에서 차단
2. `LoginId` 가 존재하지 않거나, `LoginPw` 가 저장된 값과 일치하지 않으면 `UNAUTHORIZED` (401)
   - **보안상 두 경우를 구분해서 응답하지 않는다** (ID 존재 여부 노출 방지)
3. 식별 성공 시 해당 회원을 컨텍스트로 후속 로직 수행

> 헤더 인증은 응용 계층(`UserService.authenticate`)이 담당한다. 도메인 VO 는 형식 검증만, 헤더 인증은 응용에서 결합한다.

---

## 4. 기능 요구사항

### 4.1 회원가입

**입력 필드**
| 필드 | 타입 | 규칙 |
|---|---|---|
| `loginId` | String | 영문/숫자만, 길이 제약, 유일 |
| `password` | String | 비밀번호 RULE (아래 4.4) |
| `name` | String | 형식 규칙 (아래) |
| `birthDate` | LocalDate | 형식 규칙 (아래) |
| `email` | String | 이메일 형식 |

**불변 규칙**
- 이미 가입된 `loginId` 로 가입 시 `DUPLICATE_LOGIN_ID` (409)
- 이미 가입된 `email` 로 가입 시 `DUPLICATE_EMAIL` (409)
- 비밀번호는 **장기적으로** BCrypt 등 단방향 해시로 저장한다 (현재는 평문 비교, 해시 도입은 TODO). 단, 평문 로그 출력은 지금도 금지.
- 가입 성공 시 시스템 발급 `Long id`, `createdAt` 기록 (`BaseEntity` 활용)

**검증 규칙**
- `loginId`: 영문/숫자만, **4~20자** 길이, 유일
- `name`: **한글 또는 영문만, 2~50자** (숫자/특수문자/공백 불가, 1글자 이름은 가입 거부)
- `email`:
  - 형식: `local@domain.tld` 구조를 만족 (로컬파트 1자 이상 + `@` + 도메인 + `.` + TLD 1자 이상)
  - 권장 검증: Bean Validation `@Email` + 도메인에서 추가 정규식 (`^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` 수준)
  - **최대 255자** (`Email.of` 가 `length > 255` 입력을 거부)
  - 유일 (이미 가입된 이메일 거부 — `DUPLICATE_EMAIL` 409)
- `birthDate`: `yyyy-MM-dd` 형식, **만 14세 이상**, 미래 일자 거부
  - 만 나이 계산은 가입 시점(`now()`) 기준

**성공 응답**: `200 OK` + `ApiResponse.success()` (data 는 null) — `id`/`loginId` 는 노출하지 않는다. 부가 정보 확인은 `GET /api/v1/users/me` 로 한다.
**실패 응답**: `ApiResponse` 표준 에러 — 필드/형식 검증 실패는 `SIGNUP_BAD_REQUEST` (400), 단 **비밀번호 RULE 위반은 `INVALID_PASSWORD` (400)** 으로 분리된다.

### 4.2 내 정보 조회

**인증**: 3장의 헤더 기반 식별을 따른다.
**요청 바디**: 없음.

**반환 정보**
| 필드 | 비고 |
|---|---|
| `loginId` | 영문/숫자만 (그대로 반환) |
| `name` | **마지막 글자 마스킹** (예: `홍길동` → `홍길*`) |
| `birthDate` | `yyyy-MM-dd` |
| `email` | 그대로 반환 |

**마스킹 규칙**
- 마스킹 문자는 `*` 로 통일
- 이름이 N글자일 때 마지막 1글자를 `*` 하나로 치환
  - 예: `김수` → `김*`, `홍길동` → `홍길*`, `Anne` → `Ann*`
- 가입 검증 단계에서 `name` 은 최소 2글자 이상이므로 1글자 케이스는 발생하지 않는다

### 4.3 비밀번호 수정

**인증**: 3장의 헤더 기반 식별을 따른다.

**요청 바디**
| 필드 | 의미 |
|---|---|
| `prevPw` | 기존 비밀번호 |
| `nextPw` | 새 비밀번호 |

**규칙**
- 헤더의 `X-Loopers-LoginPw` 와 body 의 `prevPw` 가 **둘 다 일치**해야 한다 (이중 확인).
  - 일치하지 않으면 `UNAUTHORIZED` (401)
- `nextPw` 는 비밀번호 RULE (4.4) 을 만족해야 한다 — 위반 시 `INVALID_PASSWORD` (400)
  (Password VO 의 RULE 검증은 가입/변경 양쪽 모두 `INVALID_PASSWORD` 로 통일)
- `nextPw` 는 **현재 비밀번호와 동일할 수 없다** — 위반 시 `PASSWORD_CHANGE_BAD_REQUEST` (400). 검사는 도메인(`User.changePassword`) 이 수행.
- 변경 성공 시 (현재는) 새 평문을 그대로 저장. BCrypt 해시 재계산 도입은 TODO.

**성공 응답**: `200 OK` + `ApiResponse.success()` (data 는 null)

### 4.4 비밀번호 RULE (공용)

가입 시 / 변경 시 모두 적용한다.

1. **8~16자** 길이
2. **영문 대소문자, 숫자, 특수문자**만 사용 가능 (그 외 문자 불가)
3. **3개 카테고리 — 영문(대/소 통합), 숫자, 특수문자 — 에서 각각 최소 1글자씩** 포함해야 한다
   - 예: `Asdf1234!` ✅ / `asdfasdf` ❌ (숫자/특수문자 없음) / `12345678` ❌ (영문/특수문자 없음)
4. **생년월일을 비밀번호 내에 포함할 수 없다**
   - 검사 대상 포맷: **`yyyyMMdd`, `yyMMdd`** 두 가지
   - 부분 문자열로 포함되면 위반 (예: 생년월일 `1990-05-10` → 비번에 `19900510` 또는 `900510` 포함 시 거부)
   - `MMdd` (`0510`) 같은 짧은 포맷은 오탐 위험으로 검사 대상에서 제외

---

## 5. 도메인 규칙 / 불변식

- `Email`, `LoginId`, `Password` 는 **값 객체(Value Object)** 로 표현한다.
  - 형식 검증을 생성 시점(`Email.of` / `LoginId.of` / `Password.of`)에 수행 → 인스턴스는 항상 유효
  - `Password.of` 는 RULE 위반 시 **항상 `INVALID_PASSWORD` 를 던진다** (호출 측 `errorType` 매개변수 없음 — 가입/변경 모두 동일 에러 타입 사용).
- `Password` 값 객체는 평문/해시 두 상태를 표현할 수 있는 형태로 두되, **현재 구현은 평문 비교**다 (BCrypt 도입은 TODO).
- `User` 의 `password` 는 어떤 외부 응답 DTO 에도 포함되지 않는다 (해시조차 포함 금지).
- 도메인 단에서 검증이 끝나야 하며, 컨트롤러는 검증 위임만 한다 (Bean Validation 으로 1차 검증, 도메인에서 최종 검증).
- 규칙 위치 정리:
  - 형식 검증 → VO 팩토리 (`Email.of` / `Password.of` / `LoginId.of`)
  - "새 비밀번호 ≠ 현재 비밀번호" 검증 → 도메인 (`User.changePassword`)
  - 헤더 인증 (loginId 조회 + password 일치) → 응용 (`UserService.authenticate`)

---

## 6. 에러 코드

`com.loopers.domain.user.UserErrorType` (구현체는 `com.loopers.support.error.ErrorType` 인터페이스) 에 다음을 사용한다.

| 코드 | HTTP | 발생 케이스 |
|---|---|---|
| `SIGNUP_BAD_REQUEST` | 400 | 회원가입 시 `loginId` / `name` / `email` / `birthDate` 형식·길이 검증 실패 |
| `INVALID_PASSWORD` | 400 | `Password` VO RULE 검증 실패 (길이/문자/카테고리/생년월일 포함) — **가입/비밀번호 변경 양쪽 공통** |
| `PASSWORD_CHANGE_BAD_REQUEST` | 400 | 비밀번호 수정 시 `nextPw` 가 현재 비밀번호와 동일 (`User.changePassword` 의 도메인 검증) |
| `UNAUTHORIZED` | 401 | 헤더 누락 / ID 존재하지 않음 / 비밀번호 불일치 / 비밀번호 수정 body `prevPw` 불일치 (응답에서 구분하지 않음) |
| `DUPLICATE_LOGIN_ID` | 409 | 가입 시 동일 loginId 존재 |
| `DUPLICATE_EMAIL` | 409 | 가입 시 동일 email 존재 |

---

## 7. API 사양 (요약)

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/users` | 불필요 | 회원가입 |
| `GET` | `/api/v1/users/me` | 헤더 필수 | 내 정보 조회 |
| `PATCH` | `/api/v1/users/me/password` | 헤더 필수 | 비밀번호 수정 |

상세 요청/응답 스키마는 `interfaces.api.user.UserV1Dto` 에 정의하고 `UserV1ApiSpec` 에 OpenAPI 시그니처를 둔다. 전체 스펙은 [docs/week1/api-spec.md](./api-spec.md) 를 진실의 원천으로 본다.

---

## 8. 비기능 요구사항

- 모든 응답은 `ApiResponse<T>` 로 감싼다 (No Content 응답 제외).
- `CoreException(ErrorType)` 으로 도메인 예외를 던지고 `ApiControllerAdvice` 가 표준 변환.
- 비밀번호 평문/해시는 로그에 절대 출력하지 않는다.
- 쓰기 작업: `@Transactional` / 읽기: `@Transactional(readOnly = true)`.

---

## 9. 데이터 모델 (논리)

`user` 가 MySQL 일부 버전/모드에서 예약어 충돌을 일으킬 수 있어 테이블명은 복수형 `users` 로 한다.

```
users
├── id              BIGINT       PK, auto-increment
├── login_id        VARCHAR(20)  UNIQUE, NOT NULL
├── password        VARCHAR(255) NOT NULL  (현재 평문 저장 — BCrypt 해시 도입은 TODO. 컬럼 폭은 해시 대비 255 유지)
├── name            VARCHAR(50)  NOT NULL
├── birth_date      DATE         NOT NULL
├── email           VARCHAR(255) UNIQUE, NOT NULL
├── created_at      DATETIME     NOT NULL  (BaseEntity)
└── updated_at      DATETIME     NOT NULL  (BaseEntity)
```

---

## 10. 수용 기준 (Acceptance Criteria) — 종합

Week 1 종료 시점에 다음이 모두 만족해야 한다.

- [ ] 회원가입 / 내 정보 조회 / 비밀번호 수정 3개 API 가 동작한다
- [ ] 모든 기능에 도메인 단위 테스트 + 통합 테스트 + 컨트롤러 E2E 가 존재한다
- [ ] 비밀번호 평문/해시 어떤 형태로도 응답 DTO 와 로그에 노출되지 않는다 (저장 형태의 해시화는 후속 TODO)
- [ ] 이름 마스킹이 내 정보 조회 응답에서 일관되게 적용된다
- [ ] 모든 테스트와 `./gradlew ktlintCheck` 가 통과한다
- [ ] [docs/week1/plan.md](./plan.md) 의 모든 항목이 `- [x]` 로 마감된다