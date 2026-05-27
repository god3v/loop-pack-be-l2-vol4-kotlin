# Week 1 — User 도메인 API 명세

> **입력 명세**: [requirements.md (v1.1)](./requirements.md)
> **작성일**: 2026-05-13
> **최종 동기화**: 2026-05-15 — 실제 컨트롤러 구현과 일치하도록 갱신
> **버전**: v1.1
> **Base URL**: `/api/v1`

---

## 0. 공통 사항

### 0.1 헤더 기반 사용자 식별

인증이 필요한 엔드포인트는 아래 두 헤더를 **모두** 요구한다.

| 헤더 | 필수 | 의미 |
|---|---|---|
| `X-Loopers-LoginId` | O | 로그인 ID |
| `X-Loopers-LoginPw` | O | 비밀번호 (현재는 평문 비교, BCrypt 해시는 후속 작업) |

식별 절차:
1. 헤더 중 하나라도 누락/공백 → `401 UNAUTHORIZED` (Controller 의 `requireAuthHeaders` 가 차단)
2. `LoginId` 미존재 또는 `LoginPw` 불일치 → `401 UNAUTHORIZED` (응답에서 구분 X)

> **TODO**: `Password` 도메인 VO 에 해시 도입 예정. 현재는 `Password.matches` 가 평문 동등성 비교.

### 0.2 엔드포인트 일람

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST`  | `/api/v1/users` | 불필요 | 회원가입 |
| `GET`   | `/api/v1/users/me` | 헤더 필수 | 내 정보 조회 |
| `PATCH` | `/api/v1/users/me/password` | 헤더 필수 | 비밀번호 수정 |

---

## 1. 회원가입

### Request
- `POST /api/v1/users`
- **인증**: 불필요
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "loginId":   "loopers01",
  "password":  "Asdf1234!",
  "name":      "홍길동",
  "birthDate": "1999-12-31",
  "email":     "local@domain.tld"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `loginId` | String | O | 영문/숫자만, 4~20자, 유일 |
| `password` | String | O | 길이 8~16자, 영문/숫자/특수문자 각 1자 이상, ASCII 인쇄 가능 문자 `!`~`~`, 생년월일(`yyyyMMdd` 또는 `yyMMdd`) 포함 불가 |
| `name` | String | O | 한글 또는 영문만, 2~50자 (숫자/특수문자/공백 불가) |
| `birthDate` | String (`yyyy-MM-dd`) | O | 만 14세 이상, 미래 일자 거부 |
| `email` | String | O | `local@domain.tld` 형식, 최대 255자, 유일 |

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | null | `ApiResponse.success()` — 생성된 식별자/loginId 를 응답으로 노출하지 않는다 |

> 내부적으로 `UserFacade.signup` 은 `SignupResult(id, loginId)` 를 반환하지만, Controller 가 `data` 로 노출하지 않는다.

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `SIGNUP_BAD_REQUEST` | loginId/name/email/birthDate 형식·길이·자격 위반 |
| `400` | `INVALID_PASSWORD` | password RULE 위반 (길이/문자/카테고리/생년월일 포함) — `Password.of` 가 던짐 |
| `409` | `DUPLICATE_LOGIN_ID` | 동일 loginId 가 이미 존재 |
| `409` | `DUPLICATE_EMAIL` | 동일 email 이 이미 존재 |

---

## 2. 내 정보 조회

### Request
- `GET /api/v1/users/me`
- **인증**: 헤더 필수 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`)
- **Content-Type**: — (요청 바디 없음)

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "loginId":   "loopers01",
    "name":      "홍길*",
    "birthDate": "1995-08-21",
    "email":     "hong@example.com"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.loginId` | String | 가입 시 입력값 그대로 반환 |
| `data.name` | String | **마지막 1글자 마스킹 (`*`)** — `홍길동` → `홍길*`, `김수` → `김*`, `Anne` → `Ann*` |
| `data.birthDate` | String (`yyyy-MM-dd`) | 그대로 반환 |
| `data.email` | String | 그대로 반환 |

> `password` 는 평문/해시 어느 쪽도 응답에 포함하지 않는다.

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 헤더 누락, loginId 미존재, 비밀번호 불일치 (응답에서 구분 X) |

---

## 3. 비밀번호 수정

### Request
- `PATCH /api/v1/users/me/password`
- **인증**: 헤더 필수 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`)
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "prevPw": "Asdf1234!",
  "nextPw": "Qwer5678@"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `prevPw` | String | O | 현재 비밀번호. 도메인 (`User.changePassword`) 이 저장된 비밀번호와 일치 여부 검증 (이중 확인) |
| `nextPw` | String | O | 비밀번호 RULE (requirements §4.4) 만족 + `prevPw` 와 다를 것 |

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | null | `ApiResponse.success()` — 응답 바디 없음 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `INVALID_PASSWORD` | `nextPw` RULE 위반 — `Password.of` 가 던짐 |
| `400` | `PASSWORD_CHANGE_BAD_REQUEST` | `nextPw` 가 현재 비밀번호와 동일 — `User.changePassword` 가 던짐 |
| `401` | `UNAUTHORIZED` | 헤더 인증 실패, body `prevPw` 와 저장된 비밀번호 불일치 |

---

## 부록 — 응답 ErrorType 정리

| code | HTTP | 발생 지점 |
|---|---|---|
| `SIGNUP_BAD_REQUEST` | 400 | 회원가입 — loginId/name/birthDate/email 도메인 검증 실패 (`User.init` / `Email.of`) |
| `INVALID_PASSWORD` | 400 | 비밀번호 RULE 위반 — 가입·변경 양쪽 (`Password.of`) |
| `PASSWORD_CHANGE_BAD_REQUEST` | 400 | 비밀번호 수정 — 새 비번이 현재와 동일 (`User.changePassword`) |
| `UNAUTHORIZED` | 401 | 헤더 누락, 인증 실패, 비밀번호 수정 시 body `prevPw` 불일치 |
| `DUPLICATE_LOGIN_ID` | 409 | 회원가입 — loginId 중복 |
| `DUPLICATE_EMAIL` | 409 | 회원가입 — email 중복 |
