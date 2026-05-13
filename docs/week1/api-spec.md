# Week 1 — User 도메인 API 명세

> **입력 명세**: [requirements.md (v1.1)](./requirements.md)
> **작성일**: 2026-05-13
> **버전**: v1.0
> **Base URL**: `/api/v1`

---

## 0. 공통 사항

### 0.1 헤더 기반 사용자 식별

인증이 필요한 엔드포인트는 아래 두 헤더를 **모두** 요구한다.

| 헤더 | 필수 | 의미 |
|---|---|---|
| `X-Loopers-LoginId` | O | 로그인 ID |
| `X-Loopers-LoginPw` | O | 비밀번호 (평문, 서버에서 BCrypt 비교) |

식별 절차:
1. 헤더 중 하나라도 누락 → `401 UNAUTHORIZED`
2. `LoginId` 미존재 또는 `LoginPw` 해시 불일치 → `401 UNAUTHORIZED`

### 0.2 엔드포인트 일람

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/users` | 불필요 | 회원가입 |
| `GET`   | `/api/v1/users/me` | 헤더 필수 | 내 정보 조회 |
| `PATCH` | `/api/v1/users/me/password` | 헤더 필수 | 비밀번호 수정 |

---

## 1. 회원가입

### Request
-`POST /api/v1/users`
- **인증**: 불필요
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "loginId":   "userid",
  "password":  "Asdf1234!",
  "name":      "홍길동",
  "birthDate": "1999-12-31",
  "email":     "local@domain.tld"
}
```

| 필드 | 타입 | 필수 | 규칙                                      |
|---|---|---|-----------------------------------------|
| `loginId` | String | O | 영문/숫자만, 4~20자, 유일                       |
| `password` | String | O | 영문/숫자/특수문자 각각 최소 1글자, 생년월일 포함 불가, 8~16자 |
| `name` | String | O | 한글 또는 영문만, 2~50자 (숫자/특수문자/공백 불가)        |
| `birthDate` | String (`yyyy-MM-dd`) | O | 만 14세 이상, 미래 일자 거부                      |
| `email` | String | O | `local@domain.tld` 형식, 6~254자           |

### Response
- `201 Created`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":      1,
    "loginId": "loopers01"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | 시스템 발급 식별자 |
| `data.loginId` | String | 가입 시 입력값 그대로 반환 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `BAD_REQUEST` | 입력 형식/길이 위반, 비밀번호 RULE 위반, birthDate 자격 미달 |
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

| 필드 | 타입 | 비고                                                                       |
|---|---|--------------------------------------------------------------------------|
| `data.loginId` | String | 가입 시 입력값 그대로 반환                                                          |
| `data.name` | String | **마지막 1글자 마스킹 (`*`)** — `홍길동` → `홍길*`, `김수` → `김*`, `Anne` → `Ann*` |
| `data.birthDate` | String (`yyyy-MM-dd`) | 그대로 반환                                                                    |
| `data.email` | String | 그대로 반환                                                                    |

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
  "currentPassword": "Asdf1234!",
  "newPassword":     "Qwer5678@"
}
```

| 필드 | 타입 | 필수 | 규칙                                                       |
|---|---|---|----------------------------------------------------------|
| `currentPassword` | String | O | 기존 비밀번호. 헤더 `X-Loopers-LoginPw` 와 **동일해야 함** (이중 확인)      |
| `newPassword` | String | O | 비밀번호 RULE (requirements §4.4) 만족 + `currentPassword` 와 다를 것 |

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
| `data` | null | 응답 바디 없음 (`ApiResponse.success()`) |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `BAD_REQUEST` | `newPassword` RULE 위반, `currentPassword` 와 동일 |
| `401` | `UNAUTHORIZED` | 헤더 인증 실패, 헤더 `X-Loopers-LoginPw` 와 body `currentPassword` 불일치 |

