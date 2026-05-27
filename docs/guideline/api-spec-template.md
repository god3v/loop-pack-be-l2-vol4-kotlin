# API 명세 템플릿

> 새 도메인/주차의 API 명세를 작성할 때 본 템플릿을 복사해 사용한다.
> 위치 예: `docs/weekN/api-spec.md`
> 본 문서는 **공통 응답 포맷** 과 **엔드포인트별 작성 규약** 두 부분으로 구성된다.

---

## 0. 공통 응답 포맷 — `ApiResponse<T>`

모든 응답은 `com.loopers.interfaces.api.ApiResponse<T>` 로 감싼다.

### 0.1 구조

```jsonc
{
  "meta": {
    "result":    "SUCCESS" | "FAIL",
    "errorCode": "string | null",
    "message":   "string | null"
  },
  "data": "T | null"
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `meta.result` | `"SUCCESS"` \| `"FAIL"` | 응답 결과 |
| `meta.errorCode` | String? | 실패 시 `ErrorType.code` (예: `BAD_REQUEST`). 성공 시 `null` |
| `meta.message` | String? | 실패 시 사용자 메시지. 성공 시 `null` |
| `data` | T? | 성공 시 페이로드. 실패 시 `null`. 응답 바디가 비는 성공도 `null` |

### 0.2 성공 응답 예시

데이터가 있는 경우:

```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":      1,
    "loginId": "loopers01"
  }
}
```

데이터가 없는 경우 (`ApiResponse.success()`):

```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": null
}
```

### 0.3 실패 응답 예시

```jsonc
{
  "meta": {
    "result":    "FAIL",
    "errorCode": "BAD_REQUEST",
    "message":   "잘못된 요청이다."
  },
  "data": null
}
```

```jsonc
{
  "meta": {
    "result":    "FAIL",
    "errorCode": "UNAUTHORIZED",
    "message":   "인증에 실패하였다."
  },
  "data": null
}
```

```jsonc
{
  "meta": {
    "result":    "FAIL",
    "errorCode": "DUPLICATE_LOGIN_ID",
    "message":   "이미 사용 중인 로그인 ID 다."
  },
  "data": null
}
```

### 0.4 표준 에러 코드

`com.loopers.support.error.ErrorType` 를 따른다. 본 표는 자주 쓰는 코드 일람이며, 도메인별 추가 코드는 각 명세 문서의 "실패 응답" 표에서 명시한다.

| HTTP | errorCode | 의미 |
|---|---|---|
| `400` | `BAD_REQUEST` | 입력 형식/길이 검증 실패, 비즈니스 규칙 위반 |
| `401` | `UNAUTHORIZED` | 인증 실패 (헤더 누락/계정 없음/비번 불일치, 응답에서 구분 X) |
| `404` | `NOT_FOUND` | 존재하지 않는 리소스 |
| `409` | `CONFLICT` | 리소스 중복/충돌 (예: `DUPLICATE_LOGIN_ID`, `DUPLICATE_EMAIL`) |
| `500` | `INTERNAL_ERROR` | 처리 중 알 수 없는 오류 |

---

## 1. 엔드포인트 작성 규약

각 엔드포인트는 아래 5개 블록 순서로 작성한다. 추가 블록(공통 헤더, 시퀀스 다이어그램 등)이 필요하면 "실패 응답" 뒤에 둔다.

```
## N. {엔드포인트 한국어 이름}

### Request
- `METHOD /api/v1/...`
- **인증**: {불필요 | 헤더 필수 (...)}
- **Content-Type**: `application/json` | — (요청 바디 없음)

**Request Body**
```jsonc
{ ... }
```

| 필드 | 타입 | 필수 | 규칙 |
| ... |

### Response
- `200 OK` (또는 `201 Created` 등)

**Response Body**
```jsonc
{ ... }
```

| 필드 | 타입 | 비고 |
| ... |

### 실패 응답

| HTTP | errorCode | 케이스 |
| ... |
```

### 작성 체크리스트

- [ ] 메서드/경로/Content-Type/인증 요건이 `### Request` 의 bullet 3줄에 모두 적혀 있다
- [ ] Request Body 가 없으면 "**Request Body**: 없음" 으로 명시한다
- [ ] Request/Response 모두 **JSON 예시 + 필드 표** 를 짝으로 둔다
- [ ] 응답 데이터가 없는 성공도 `data: null` 인 JSON 예시를 보여준다
- [ ] 실패 응답 표는 HTTP / errorCode / 케이스 3개 컬럼을 유지한다 (`0.4` 의 표준 코드 + 도메인 추가 코드)
- [ ] 실패 응답 JSON 본문은 **반복하지 않는다** — 본 템플릿 §0.3 을 참조하도록 한다
- [ ] 비밀번호/토큰 등 민감 필드는 어떤 응답 예시에도 포함하지 않는다

---

## 2. 빈 엔드포인트 블록 (복사용)

아래 블록을 그대로 복사해 채워 사용한다.

```
## N. {엔드포인트 이름}

### Request
- `METHOD /api/v1/{path}`
- **인증**: 불필요
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "field1": "string (필수, 설명)",
  "field2": "string (선택, 설명)"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `field1` | String | O | ... |
| `field2` | String | X | ... |

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id": 1
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | ... |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `BAD_REQUEST` | ... |