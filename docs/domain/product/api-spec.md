# 상품(Product) API 명세

> 본 문서는 `docs/guideline/api-spec-template.md` 의 규약을 따른다.
> 요구사항: [requirements.md (v0.3)](./requirements.md)
> 작성일: 2026-06-09
> Base URL: 회원 채널 `/api/v1`, 관리자 채널 `/api-admin/v1`
> 브랜드 엔드포인트는 [brand/api-spec.md](../brand/api-spec.md) 참조.

---

## 0. 공통 사항

### 0.1 회원 채널 인증 — 선택
카탈로그 조회(UC-1·UC-2)는 **인증을 요구하지 않는다**. 인증 헤더(`X-Loopers-LoginId`/`X-Loopers-LoginPw`)가 함께 오면 회원이 식별되어 상세의 `likedByMe` 가 의미를 가진다. 인증이 없거나 실패해도 **거부되지 않으며**, `likedByMe` 는 거짓으로 응답된다.

### 0.2 관리자 채널 인증
관리자 동작(UC-9~13)은 관리자 인증을 통과해야 한다. 인증 설계는 [admin/logical-model.md](../admin/logical-model.md) 가 정의하며, 아래 헤더로 관리자를 식별한다.

| 헤더 | 필수 | 값 | 의미 |
|---|---|---|---|
| `X-Loopers-Ldap` | O | `loopers.admin` | 관리자 식별자 |

`/api-admin/**` 는 경로 기준으로 전수 인증되며(관리자 전용 인터셉터), 회원 채널(`/api/**`)과 분리된다. 헤더 누락 또는 값 불일치는 `401 UNAUTHORIZED` 로 분기한다. (관리자는 별도 도메인 모델/계정 저장소를 두지 않는다 — 헤더 식별만.)

### 0.3 판매 상태(SalesStatus) 표기
관리자 응답에 포함되는 `salesStatus` 의 와이어 값은 도메인 `SalesStatus.key`(snake_case)를 사용한다.

| 값 | 의미 |
|---|---|
| `on_sale` | 판매중 |
| `out_of_stock` | 품절 |
| `off_sale` | 판매중지 |

### 0.4 엔드포인트 일람

| Method | Path | 채널 | 인증 | 설명 |
|---|---|---|---|---|
| `GET`    | `/api/v1/products`               | 회원   | 선택 | 상품 목록 조회 (UC-1) |
| `GET`    | `/api/v1/products/{productId}`   | 회원   | 선택 | 상품 상세 조회 (UC-2) |
| `GET`    | `/api-admin/v1/products`             | 관리자 | 필수 | 상품 목록 조회 (UC-9) |
| `GET`    | `/api-admin/v1/products/{productId}` | 관리자 | 필수 | 상품 상세 조회 (UC-10) |
| `POST`   | `/api-admin/v1/products`             | 관리자 | 필수 | 상품 등록 (UC-11) |
| `PUT`    | `/api-admin/v1/products/{productId}` | 관리자 | 필수 | 상품 정보 수정 (UC-12) |
| `DELETE` | `/api-admin/v1/products/{productId}` | 관리자 | 필수 | 상품 삭제 (UC-13) |

---

## 1. (회원) 상품 목록 조회

### Request
- `GET /api/v1/products?sort={sort}&brandId={brandId}&page={page}&size={size}`
- **인증**: 선택 (`§0.1`) — 본 엔드포인트 응답에는 `likedByMe` 가 없어 인증이 결과에 영향을 주지 않는다
- **Content-Type**: — (요청 바디 없음)

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `sort` | String | X | `latest` | `latest` · `price_asc` · `likes_desc` 중 하나. 그 외 값은 거부 |
| `brandId` | Long | X | — | 지정 시 해당 브랜드 소속 상품만 |
| `page` | Int | X | `0` | 0부터 시작. Spring `Pageable` 이 음수를 `0` 으로 보정 |
| `size` | Int | X | `20` | 페이지 크기. 상한 초과 시 상한값으로 보정 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": [
    {
      "id":        101,
      "name":      "운동화",
      "price":     59000,
      "likeCount": 42,
      "brandId":   7
    }
  ]
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | Array | 삭제 마크되지 않은 상품 요약 목록. `sort` 순서. 없으면 빈 배열 `[]` |
| `data[].id` | Long | 상품 식별자 |
| `data[].name` | String | 상품 이름 |
| `data[].price` | Long | 상품 가격 |
| `data[].likeCount` | Long | 좋아요 수 |
| `data[].brandId` | Long | 브랜드 식별자 |

> 현재 `ProductFacade.getProducts` 는 `List<ProductSummaryResult>`(평면 배열) 을 반환한다 — 페이지 메타는 포함되지 않는다(부록 참조).

### 실패 응답

> 실패 응답 JSON 본문 형태는 템플릿 `§0.3` 을 참조한다.

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `PRODUCT_BAD_REQUEST` | `sort` 가 허용 집합(`latest`/`price_asc`/`likes_desc`) 밖 (`ProductSortType.from`) |

> requirements §UC-1 E1/E3 는 페이지 크기/번호 위반을 `400` 으로 규정했으나, 페이징은 Spring `Pageable` 로 수신해 정규화되므로 페이징 입력으로 인한 `400` 은 발생하지 않는다(`like` 도메인과 동일 결정).

---

## 2. (회원) 상품 상세 조회

### Request
- `GET /api/v1/products/{productId}`
- **인증**: 선택 (`§0.1`) — 헤더 동반 시 `likedByMe` 가 채워지고, 미인증/실패는 거부 없이 `likedByMe=false`
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `productId` | Long | O | 조회 대상 상품 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":        101,
    "name":      "운동화",
    "price":     59000,
    "likeCount": 42,
    "brandId":   7,
    "brandName": "나이키",
    "likedByMe": false
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | 상품 식별자 |
| `data.name` | String | 상품 이름 |
| `data.price` | Long | 상품 가격 |
| `data.likeCount` | Long | 좋아요 수 |
| `data.brandId` | Long | 브랜드 식별자 |
| `data.brandName` | String | 브랜드 이름 |
| `data.likedByMe` | Boolean | 인증 회원의 좋아요 여부. 비회원/미인증은 항상 `false` |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `404` | `PRODUCT_NOT_FOUND` | 상품이 존재하지 않거나 삭제 마크됨 |

---

## 3. (관리자) 상품 목록 조회

### Request
- `GET /api-admin/v1/products?brandId={brandId}&page={page}&size={size}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 규칙 |
|---|---|---|---|---|
| `brandId` | Long | X | — | 지정 시 해당 브랜드 소속 상품만 |
| `page` | Int | X | `0` | 0부터 시작 |
| `size` | Int | X | `20` | 페이지 크기 |

**Request Body**: 없음

### Response
- `200 OK`
- 정렬: **최신순 고정** (정렬 옵션 입력 없음)

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": [
    {
      "id":          101,
      "name":        "운동화",
      "price":       59000,
      "likeCount":   42,
      "brandId":     7,
      "salesStatus": "on_sale"
    }
  ]
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | Array | 삭제 마크되지 않은 상품 요약 목록(최신순). 없으면 빈 배열 `[]` |
| `data[].id` | Long | 상품 식별자 |
| `data[].name` | String | 상품 이름 |
| `data[].price` | Long | 상품 가격 |
| `data[].likeCount` | Long | 좋아요 수 |
| `data[].brandId` | Long | 브랜드 식별자 |
| `data[].salesStatus` | String | 판매 상태 (`§0.3`) |

> 현재 `getProductsForAdmin` 은 `List`(평면 배열) 반환 — 페이지 메타 미포함(부록 참조).

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |

---

## 4. (관리자) 상품 상세 조회

### Request
- `GET /api-admin/v1/products/{productId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `productId` | Long | O | 조회 대상 상품 식별자 |

**Request Body**: 없음

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":          101,
    "name":        "운동화",
    "price":       59000,
    "likeCount":   42,
    "brandId":     7,
    "brandName":   "나이키",
    "salesStatus": "on_sale"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data.id` | Long | 상품 식별자 |
| `data.name` | String | 상품 이름 |
| `data.price` | Long | 상품 가격 |
| `data.likeCount` | Long | 좋아요 수 |
| `data.brandId` | Long | 브랜드 식별자 |
| `data.brandName` | String | 브랜드 이름 |
| `data.salesStatus` | String | 판매 상태 (`§0.3`) |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |
| `404` | `PRODUCT_NOT_FOUND` | 상품이 존재하지 않거나 삭제 마크됨 |

---

## 5. (관리자) 상품 등록

### Request
- `POST /api-admin/v1/products`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: `application/json`

**Request Body**
```jsonc
{
  "brandId": 7,
  "name":    "운동화",
  "price":   59000,
  "stock":   100
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `brandId` | Long | O | 소속 브랜드. 이미 존재하고 삭제 마크되지 않아야 한다 |
| `name` | String | O | 상품 이름. 같은 브랜드 안에서 유일 |
| `price` | Long | O | 가격. 음수 불가 |
| `stock` | Int | O | 초기 재고 |

> 판매 상태는 등록 시 초깃값 `on_sale` 로 설정된다(입력에서 받지 않는다).

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":          101,
    "name":        "운동화",
    "price":       59000,
    "likeCount":   0,
    "brandId":     7,
    "brandName":   "나이키",
    "salesStatus": "on_sale"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | Object | 등록된 상품 상세 (관리자 상세와 동일 형태). `stock` 은 응답에 포함되지 않는다 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `PRODUCT_BAD_REQUEST` | 입력이 필수·형식 규칙을 어김 (이름/가격/재고) |
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |
| `404` | `BRAND_NOT_FOUND` | 지정 브랜드가 존재하지 않거나 삭제 마크됨 |
| `409` | `DUPLICATE_PRODUCT_NAME` | 같은 브랜드 안에 같은 이름의 상품이 이미 존재 |

---

## 6. (관리자) 상품 정보 수정

### Request
- `PUT /api-admin/v1/products/{productId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: `application/json`

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `productId` | Long | O | 수정 대상 상품 식별자 |

**Request Body**
```jsonc
{
  "name":        "운동화 v2",
  "price":       64000,
  "salesStatus": "off_sale"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | String | O | 변경할 이름. 같은 브랜드 안에서 자기 자신 제외 유일 |
| `price` | Long | O | 변경할 가격. 음수 불가 |
| `salesStatus` | String | O | 변경할 판매 상태 (`§0.3`) |

> **`brandId` 는 수정 입력에 받지 않는다** — 상품의 브랜드는 등록 이후 변경 불가(requirements §5 브랜드 불변). 재고(`stock`)도 본 수정 입력에 포함되지 않는다.

### Response
- `200 OK`

**Response Body**
```jsonc
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "id":          101,
    "name":        "운동화 v2",
    "price":       64000,
    "likeCount":   42,
    "brandId":     7,
    "brandName":   "나이키",
    "salesStatus": "off_sale"
  }
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `data` | Object | 갱신된 상품 상세 (관리자 상세와 동일 형태) |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `400` | `PRODUCT_BAD_REQUEST` | 변경 입력이 필수·형식 규칙을 어김 |
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |
| `404` | `PRODUCT_NOT_FOUND` | 상품이 존재하지 않거나 삭제 마크됨 |
| `409` | `DUPLICATE_PRODUCT_NAME` | 같은 브랜드 안에 같은 이름의 다른 상품이 존재 |

---

## 7. (관리자) 상품 삭제

### Request
- `DELETE /api-admin/v1/products/{productId}`
- **인증**: 관리자 인증 필수 — 헤더 `X-Loopers-Ldap: loopers.admin` (`§0.2`)
- **Content-Type**: — (요청 바디 없음)

| 경로 변수 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `productId` | Long | O | 삭제 대상 상품 식별자 |

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
| `data` | null | 빈 페이로드. 상품을 **삭제 마크**(soft delete) 처리한다. 좋아요 행은 제거하지 않으며, `like` 도메인의 내 좋아요 목록에서 "삭제된 상품" 으로 가려진다 |

### 실패 응답

| HTTP | errorCode | 케이스 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 관리자 인증 실패 (헤더 누락/미존재/불일치) |
| `404` | `PRODUCT_NOT_FOUND` | 상품이 존재하지 않거나 이미 삭제 마크됨 |

---

## 부록 — 응답 ErrorType 정리

| code | HTTP | 발생 지점 |
|---|---|---|
| `PRODUCT_BAD_REQUEST` | 400 | 목록 정렬 위반 / 상품 등록·수정 입력 위반 (`ProductErrorType.PRODUCT_BAD_REQUEST`) |
| `UNAUTHORIZED` | 401 | 관리자 엔드포인트 — 관리자 인증 실패 (admin/logical-model §4) |
| `PRODUCT_NOT_FOUND` | 404 | 상품 조회·수정·삭제 — 미존재 또는 삭제 마크 |
| `BRAND_NOT_FOUND` | 404 | 상품 등록 — 지정 브랜드 미존재/삭제 마크 (`BrandErrorType.BRAND_NOT_FOUND`) |
| `DUPLICATE_PRODUCT_NAME` | 409 | 상품 등록·수정 — 같은 브랜드 내 이름 중복 |

> 회원 카탈로그(UC-1·UC-2)는 인증을 요구하지 않으므로 `401` 로 분기하지 않는다 — 인증 부재/실패는 거부 사유가 아니다.

## 부록 — 구현 메모 / 미해결 사항

> 상품 컨트롤러(interfaces)는 아직 없다. 본 명세는 구현된 `application.product.ProductFacade` 와 도메인 모델에 정합하도록 작성했다.

- **관리자 인증 (확정)**: [admin/logical-model.md](../admin/logical-model.md) 로 확정 — 별도 도메인 모델 없이 헤더 `X-Loopers-Ldap: loopers.admin` 으로 식별, `/api-admin/**` 경로 기준 인증. 컨트롤러 구현 시 `AdminAuthInterceptor`(§5) 를 따른다.
- **`description` 없음**: requirements 의 UC-2/UC-10 은 상품 "설명" 을 언급하나, 현재 `Product` 도메인·결과 DTO 에 `description` 필드가 없다. 도입은 도메인 확장 시 결정.
- **`stock` 비노출**: 등록 입력에는 `stock` 이 있으나 응답(`AdminProductDetailResult`)에는 없다. 재고 노출이 필요하면 결과 DTO 확장.
- **페이지 메타 없음**: 회원/관리자 목록 모두 `List`(평면 배열) 반환. `like` 도메인처럼 `PageResult` 페이지 봉투 전환은 후속 결정.
- **`salesStatus` 직렬화**: 와이어 값은 `SalesStatus.key`(snake_case) 기준으로 표기했다(`§0.3`). 컨트롤러 DTO 에서 key 기반 직렬화/역직렬화(`@JsonValue`/`@JsonCreator` 또는 명시 매핑)가 필요하다 — 기본 enum 직렬화(`ON_SALE`)와 다르다.
- **`PUT` vs `PATCH`**: 수정은 변경 가능한 필드(name·price·salesStatus) 전체 치환이라 `PUT` 으로 표기했다.
- **HTTP 상태**: 등록 성공을 `200 OK` + 생성 리소스로 표기(프로젝트 컨벤션). `201 Created` 채택은 컨트롤러 구현 시 결정 가능.
- requirements §5 의 TBD: 판매 상태 **전이 규칙**, 회원 카탈로그(UC-1·UC-2)의 판매 상태 노출/필터 정책은 미해결 — 본 명세는 회원 응답에 `salesStatus` 를 포함하지 않는다(관리자 응답에만 포함, 현재 구현과 일치).
