# 쿠폰(Coupon) API 명세

> 본 문서는 `docs/guideline/api-spec-template.md` 의 규약을 따른다.
> 요구사항: [requirements.md (v0.1)](./requirements.md)
> 데이터 모델: [docs/design/04-erd.md](../../design/04-erd.md) 의 `coupons` · `user_coupons`
> 작성일: 2026-06-09
> Base URL: 회원 채널 `/api/v1`, 관리자 채널 `/api-admin/v1`

---

## 0. 공통 사항

### 0.1 회원 채널 인증 — 필수
회원 동작(UC-1·UC-2)은 회원 인증을 **요구한다**. 인증 헤더(`X-Loopers-LoginId`/`X-Loopers-LoginPw`)가 없거나 식별에 실패하면 `401 UNAUTHORIZED` 로 거부된다.

| 헤더 | 필수 | 의미 |
|---|---|---|
| `X-Loopers-LoginId` | O | 회원 로그인 식별자 |
| `X-Loopers-LoginPw` | O | 회원 비밀번호 |

### 0.2 관리자 채널 인증
관리자 동작(UC-3~8)은 관리자 인증을 통과해야 한다. 인증 설계는 [admin/logical-model.md](../admin/logical-model.md) 가 정의하며, 아래 헤더로 관리자를 식별한다.

| 헤더 | 필수 | 값 | 의미 |
|---|---|---|---|
| `X-Loopers-Ldap` | O | `loopers.admin` | 관리자 식별자 |

`/api-admin/**` 는 경로 기준으로 전수 인증되며(관리자 전용 인터셉터), 회원 채널(`/api/**`)과 분리된다. 헤더 누락 또는 값 불일치는 `401 UNAUTHORIZED` 로 분기한다.

### 0.3 할인 종류(type) 표기
와이어 값은 도메인 `DiscountType` 의 이름을 그대로 쓴다(대문자).

| 값 | 의미 | `value` 의미 |
|---|---|---|
| `FIXED` | 정액 | 할인 금액(원) |
| `RATE` | 정률 | 할인 비율(%) — 1~100 |

### 0.4 발급 쿠폰 상태(status) 표기
와이어 값은 노출 상태의 이름을 그대로 쓴다(대문자). `EXPIRED` 는 저장값이 아니라 만료 시각 경과로 파생되는 노출 상태다.

| 값 | 의미 |
|---|---|
| `AVAILABLE` | 사용 가능 |
| `USED` | 사용 완료 |
| `EXPIRED` | 만료 (미사용 + 만료 시각 경과) |

### 0.5 엔드포인트 일람

| Method | Path | 채널 | 인증 | 설명 |
|---|---|---|---|---|
| `POST`   | `/api/v1/coupons/{couponId}/issue`            | 회원   | 필수 | 쿠폰 발급 요청 (UC-1) |
| `GET`    | `/api/v1/users/me/coupons`                    | 회원   | 필수 | 내 쿠폰 목록 조회 (UC-2) |
| `GET`    | `/api-admin/v1/coupons`                       | 관리자 | 필수 | 쿠폰 템플릿 목록 조회 (UC-3) |
| `GET`    | `/api-admin/v1/coupons/{couponId}`            | 관리자 | 필수 | 쿠폰 템플릿 상세 조회 (UC-4) |
| `POST`   | `/api-admin/v1/coupons`                       | 관리자 | 필수 | 쿠폰 템플릿 등록 (UC-5) |
| `PUT`    | `/api-admin/v1/coupons/{couponId}`            | 관리자 | 필수 | 쿠폰 템플릿 수정 (UC-6) |
| `DELETE` | `/api-admin/v1/coupons/{couponId}`            | 관리자 | 필수 | 쿠폰 템플릿 삭제 (UC-7) |
| `GET`    | `/api-admin/v1/coupons/{couponId}/issues`     | 관리자 | 필수 | 특정 쿠폰 발급 내역 조회 (UC-8) |

> 쿠폰 **사용**(UC-9, 할인 계산 + 단일 사용 소진) 은 회원이 직접 호출하는 엔드포인트로 노출하지 않는다 — **주문 생성 시(`POST` 주문) 적용**된다. 주문은 발급 쿠폰 식별자(`couponId`) 를 입력으로 한 장만 받아 같은 트랜잭션에서 소진하며, 적용 실패 시 주문이 실패한다(부록 참조).

---

## 1. (회원) 쿠폰 발급 요청

### Request
- `POST /api/v1/coupons/{couponId}/issue`
- **인증**: 회원 인증 필수 (`§0.1`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 발급받을 쿠폰 **템플릿** 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "userCouponId":   501,
    "couponId":       7,
    "name":           "신규가입 10% 할인",
    "type":           "RATE",
    "value":          10,
    "minOrderAmount": 10000,
    "expiredAt":      "2026-12-31T23:59:59",
    "status":         "AVAILABLE",
    "issuedAt":       "2026-06-09T12:00:00"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.userCouponId` | Long | 발급된 쿠폰(인스턴스) 식별자 |
| `data.couponId` | Long | 원본 템플릿 식별자 |
| `data.name` | String | 쿠폰 이름 |
| `data.type` | String | 할인 종류 (`§0.3`) |
| `data.value` | Long | 할인 값 (`§0.3`) |
| `data.minOrderAmount` | Long? | 최소 주문 금액. 없으면 `null` |
| `data.expiredAt` | DateTime | 만료 시각 |
| `data.status` | String | 발급 직후 항상 `AVAILABLE` (`§0.4`) |
| `data.issuedAt` | DateTime | 발급 시각 |

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `COUPON_NOT_APPLICABLE` | 템플릿이 이미 만료됨 |
| `401` | `UNAUTHORIZED` | 회원 인증 실패 (헤더 누락/계정 없음/비번 불일치) |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 삭제 마크됨 |
| `409` | `ALREADY_ISSUED_COUPON` | 같은 템플릿을 이미 발급받음 (1인 1매) |

---

## 2. (회원) 내 쿠폰 목록 조회

### Request
- `GET /api/v1/users/me/coupons?page={page}&size={size}`
- **인증**: 회원 인증 필수 (`§0.1`)
- **Content-Type**: — (요청 바디 없음)

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `page` | Int | X | `0` | 페이지 번호. Spring `Pageable` 이 음수를 `0` 으로 보정 |
| `size` | Int | X | `20` | 페이지 크기. 상한 초과 시 상한값으로 보정 |

**Request Body**: 없음

### Response
- `200 OK`
- 정렬: **발급 최신순 고정**

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "userCouponId":   501,
        "couponId":       7,
        "name":           "신규가입 10% 할인",
        "type":           "RATE",
        "value":          10,
        "minOrderAmount": 10000,
        "expiredAt":      "2026-12-31T23:59:59",
        "status":         "AVAILABLE",
        "issuedAt":       "2026-06-09T12:00:00",
        "usedAt":         null
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 1,
    "totalPages":    1
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.content` | Array | 회원 보유 발급 쿠폰 목록(발급 최신순). 없으면 빈 배열 `[]` |
| `data.content[].userCouponId` | Long | 발급 쿠폰 식별자 |
| `data.content[].couponId` | Long | 템플릿 식별자 |
| `data.content[].name` | String | 쿠폰 이름 |
| `data.content[].type` | String | 할인 종류 (`§0.3`) |
| `data.content[].value` | Long | 할인 값 (`§0.3`) |
| `data.content[].minOrderAmount` | Long? | 최소 주문 금액. 없으면 `null` |
| `data.content[].expiredAt` | DateTime | 만료 시각 |
| `data.content[].status` | String | 노출 상태 (`§0.4`) — `EXPIRED` 는 파생 |
| `data.content[].issuedAt` | DateTime | 발급 시각 |
| `data.content[].usedAt` | DateTime? | 사용 시각. 미사용이면 `null` |
| `data.page` / `data.size` / `data.totalElements` / `data.totalPages` | — | 페이지 메타 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 회원 인증 실패 |

---

## 3. (관리자) 쿠폰 템플릿 목록 조회

### Request
- `GET /api-admin/v1/coupons?page={page}&size={size}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `page` | Int | X | `0` | 0부터 시작. Spring `Pageable` 이 음수를 `0` 으로 보정 |
| `size` | Int | X | `20` | 페이지 크기. 상한 초과 시 상한값으로 보정 |

**Request Body**: 없음

### Response
- `200 OK`
- 정렬: **최신순 고정**

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "id":             7,
        "name":           "신규가입 10% 할인",
        "type":           "RATE",
        "value":          10,
        "minOrderAmount": 10000,
        "expiredAt":      "2026-12-31T23:59:59"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 1,
    "totalPages":    1
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.content` | Array | 삭제 마크되지 않은 템플릿 목록(최신순). 없으면 빈 배열 `[]` |
| `data.content[].id` | Long | 템플릿 식별자 |
| `data.content[].name` | String | 쿠폰 이름 |
| `data.content[].type` | String | 할인 종류 (`§0.3`) |
| `data.content[].value` | Long | 할인 값 (`§0.3`) |
| `data.content[].minOrderAmount` | Long? | 최소 주문 금액. 없으면 `null` |
| `data.content[].expiredAt` | DateTime | 만료 시각 |
| `data.page` / `data.size` / `data.totalElements` / `data.totalPages` | — | 페이지 메타 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |

---

## 4. (관리자) 쿠폰 템플릿 상세 조회

### Request
- `GET /api-admin/v1/coupons/{couponId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 조회 대상 템플릿 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":             7,
    "name":           "신규가입 10% 할인",
    "type":           "RATE",
    "value":          10,
    "minOrderAmount": 10000,
    "expiredAt":      "2026-12-31T23:59:59"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | 템플릿 식별자 |
| `data.name` | String | 쿠폰 이름 |
| `data.type` | String | 할인 종류 (`§0.3`) |
| `data.value` | Long | 할인 값 (`§0.3`) |
| `data.minOrderAmount` | Long? | 최소 주문 금액. 없으면 `null` |
| `data.expiredAt` | DateTime | 만료 시각 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 삭제 마크됨 |

---

## 5. (관리자) 쿠폰 템플릿 등록

### Request
- `POST /api-admin/v1/coupons`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "name":           "신규가입 10% 할인",
  "type":           "RATE",
  "value":          10,
  "minOrderAmount": 10000,
  "expiredAt":      "2026-12-31T23:59:59"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | String | O | 쿠폰 이름. 공백 불가 |
| `type` | String | O | 할인 종류 — `FIXED` / `RATE` (`§0.3`). 그 외 값은 거부 |
| `value` | Long | O | 할인 값. 양수. `RATE` 면 1~100 |
| `minOrderAmount` | Long | X | 최소 주문 금액. 음수 불가. 미지정이면 하한 제약 없음 |
| `expiredAt` | DateTime | O | 만료 시각. 등록 시점 기준 미래 |

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":             7,
    "name":           "신규가입 10% 할인",
    "type":           "RATE",
    "value":          10,
    "minOrderAmount": 10000,
    "expiredAt":      "2026-12-31T23:59:59"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | Object | 등록된 템플릿 (상세와 동일 형태) |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `COUPON_BAD_REQUEST` | 입력이 필수·형식 규칙을 어김 (이름/할인 종류/할인 값/만료 시각) |
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |

---

## 6. (관리자) 쿠폰 템플릿 수정

### Request
- `PUT /api-admin/v1/coupons/{couponId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: `application/json`

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 수정 대상 템플릿 식별자 |

**Request Body**
```jsonc
{
  "name":           "신규가입 15% 할인",
  "type":           "RATE",
  "value":          15,
  "minOrderAmount": 20000,
  "expiredAt":      "2027-06-30T23:59:59"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | String | O | 변경할 이름. 공백 불가 |
| `type` | String | O | 변경할 할인 종류 (`§0.3`) |
| `value` | Long | O | 변경할 할인 값. 양수. `RATE` 면 1~100 |
| `minOrderAmount` | Long | X | 변경할 최소 주문 금액. 음수 불가 |
| `expiredAt` | DateTime | O | 변경할 만료 시각 |

> 이미 발급된 쿠폰은 본 수정의 영향을 받지 않는 것이 권장이나, 본 모델은 발급 쿠폰이 템플릿을 참조하므로 수정이 소급될 수 있다 — 발급 시점 조건 동결이 필요하면 발급 쿠폰에 조건 스냅샷을 도입한다(부록 참조).

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":             7,
    "name":           "신규가입 15% 할인",
    "type":           "RATE",
    "value":          15,
    "minOrderAmount": 20000,
    "expiredAt":      "2027-06-30T23:59:59"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | Object | 갱신된 템플릿 (상세와 동일 형태) |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `COUPON_BAD_REQUEST` | 변경 입력이 필수·형식 규칙을 어김 |
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 삭제 마크됨 |

---

## 7. (관리자) 쿠폰 템플릿 삭제

### Request
- `DELETE /api-admin/v1/coupons/{couponId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 삭제 대상 템플릿 식별자 |

**Request Body**: 없음

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
| `data` | null | 빈 페이로드. 템플릿을 **삭제 마크**(soft delete) 처리한다. 이미 발급된 쿠폰은 제거하지 않으며 그대로 조회·사용된다 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 이미 삭제 마크됨 |

---

## 8. (관리자) 특정 쿠폰 발급 내역 조회

### Request
- `GET /api-admin/v1/coupons/{couponId}/issues?page={page}&size={size}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `couponId` | Long | O | 발급 내역을 조회할 템플릿 식별자 |

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `page` | Int | X | `0` | 0부터 시작 |
| `size` | Int | X | `20` | 페이지 크기 |

**Request Body**: 없음

### Response
- `200 OK`
- 정렬: **발급 최신순 고정**

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "content": [
      {
        "userCouponId": 501,
        "userId":       42,
        "status":       "USED",
        "issuedAt":     "2026-06-09T12:00:00",
        "usedAt":       "2026-06-10T09:30:00"
      }
    ],
    "page":          0,
    "size":          20,
    "totalElements": 1,
    "totalPages":    1
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.content` | Array | 해당 템플릿으로 발급된 발급 쿠폰 목록(발급 최신순). 없으면 빈 배열 `[]` |
| `data.content[].userCouponId` | Long | 발급 쿠폰 식별자 |
| `data.content[].userId` | Long | 발급 회원 식별자 |
| `data.content[].status` | String | 노출 상태 (`§0.4`) |
| `data.content[].issuedAt` | DateTime | 발급 시각 |
| `data.content[].usedAt` | DateTime? | 사용 시각. 미사용이면 `null` |
| `data.page` / `data.size` / `data.totalElements` / `data.totalPages` | — | 페이지 메타 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 |
| `404` | `COUPON_NOT_FOUND` | 템플릿이 존재하지 않거나 삭제 마크됨 |

---

## 부록 — 응답 ErrorType 정리

| code | HTTP | 발생 지점 |
|---|---|---|
| `COUPON_BAD_REQUEST` | 400 | 템플릿 등록·수정 입력 위반 (이름/할인 종류/할인 값/만료 시각) |
| `COUPON_NOT_APPLICABLE` | 400 | 발급/사용 — 만료된 쿠폰, 최소 주문 금액 미달 |
| `UNAUTHORIZED` | 401 | 회원/관리자 인증 실패 |
| `COUPON_NOT_FOUND` | 404 | 템플릿 조회·발급·수정·삭제 — 미존재 또는 삭제 마크 |
| `USER_COUPON_NOT_FOUND` | 404 | 쿠폰 사용(UC-9) — 발급 쿠폰 미존재 또는 소유자 불일치 |
| `ALREADY_ISSUED_COUPON` | 409 | 발급 — 같은 템플릿 1인 1매 위반 |
| `ALREADY_USED_COUPON` | 409 | 쿠폰 사용(UC-9) — 이미 사용된 쿠폰 재사용 |

---

## 부록 — 구현 메모 / 미해결 사항

- **쿠폰 사용(UC-9) — 주문 적용으로 구현**: 본 명세는 발급·조회·관리 엔드포인트만 노출한다. 사용(할인 계산 + 단일 사용 소진) 은 별도 엔드포인트가 아니라 **주문 생성(`OrderFacade.placeOrder`)** 흐름이 `CouponFacade.applyCoupon(userId, userCouponId, orderAmount): 할인금액` 을 같은 트랜잭션에서 호출해 수행한다. 쿠폰은 **주문 1건당 한 장**(`PlaceOrderCommand.couponId` 단일 슬롯)만 적용되고, 성공 시 발급 쿠폰은 즉시 `USED` 로 소진되며 할인이 주문 합계에 반영된다. 적용 실패(`USER_COUPON_NOT_FOUND` 404 — 미존재/타인 소유, `ALREADY_USED_COUPON` 409 — 이미 사용, `COUPON_NOT_APPLICABLE` 400 — 만료/최소금액 미달) 시 예외가 전파되어 주문 전체가 롤백(주문 미생성·재고 차감 원복·쿠폰 미소진)된다. 주문 도메인은 현재 별도 결제 단계가 없어 주문 생성 성공이 곧 쿠폰 확정 소진이다.
- **상태 직렬화**: `type`(`FIXED`/`RATE`) 과 `status`(`AVAILABLE`/`USED`/`EXPIRED`) 의 와이어 값은 enum 이름(대문자) 을 그대로 쓴다 — `product` 의 `salesStatus`(snake_case key) 와 다른 컨벤션이며, 본 도메인 원 스펙의 요청 예시(`"type": "RATE"`)를 따른다.
- **`EXPIRED` 파생**: 저장 상태는 `AVAILABLE`/`USED` 둘뿐이고, `EXPIRED` 는 조회 시 `coupons.expired_at` 경과로 파생한다(만료 배치 불필요).
- **조건 동결(스냅샷) 여부**: 현재 발급 쿠폰은 템플릿을 ID 참조하므로 템플릿 수정이 미사용 발급분에 소급될 수 있다. 발급 시점 조건 동결이 요구되면 `user_coupons` 에 `discount_type`/`discount_value`/`min_order_amount`/`expired_at` 스냅샷 컬럼을 도입한다(현재 범위 밖).
- **HTTP 상태**: 등록 성공을 `200 OK` + 생성 리소스로 표기(프로젝트 컨벤션). `201 Created` 채택은 구현 시 결정 가능.
- **`PUT` vs `PATCH`**: 수정은 변경 가능한 필드 전체 치환이라 `PUT` 으로 표기했다.
