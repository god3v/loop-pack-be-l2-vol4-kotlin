# product 도메인 — TDD 진척 체크리스트

> 본 plan 은 사용자가 제공한 **4 라인 요구사항** 에만 한정한다.
> 기존 [requirements.md](./requirements.md) 의 관리자 UC-4 ~ UC-13 은 본 스코프 밖이며, plan / 코드와 requirements.md 의 불일치는 의도된 상태다.
> brand 도메인은 product 안에서 **최소 골격** (id · name) 만 둔다 — 별도 requirements 는 추후.

---

## 0. 스코프 — 사용자 제공 요구사항

1. 상품 정보 객체는 브랜드 정보, 좋아요 수를 포함한다.
2. 상품의 정렬 조건(`latest` · `price_asc` · `likes_desc`) 을 고려한 조회 기능.
3. 상품은 재고를 가지고 있고, 주문 시 차감할 수 있어야 한다.
4. 재고의 음수 방지 처리는 도메인 레벨에서 처리된다.

### 결정 사항
- `likeCount` 는 **`Product` 의 비정규화 필드** 로 둔다 — 외부 like 도메인이 갱신할 책임. 현 스코프에서는 필드만 가진다.
- 사용자 카탈로그(목록·상세) 조회 흐름만 다룬다. 관리자·삭제 마크는 다루지 않는다.
- 주문에 의한 재고 차감은 `Product.deductStock(quantity)` 메서드를 도메인이 제공하는 데까지가 본 도메인 책임. 실제 주문 유스케이스는 order 도메인 차례.

---

## 1. Phase 1 — Domain (`com.loopers.domain.product` · `com.loopers.domain.brand`)

순수 Kotlin. Spring / JPA / Repository 의존 금지.

### 1.1 Product 애그리거트 — 생성 불변식
- [x] Product 를 음수 price 로 생성하면 예외가 발생한다
- [x] Product 를 음수 stock 으로 생성하면 예외가 발생한다
- [x] Product 를 음수 likeCount 로 생성하면 예외가 발생한다
- [x] Product 를 blank name 으로 생성하면 예외가 발생한다

### 1.2 Product 재고 차감 (`deductStock`)
- [x] 양수 quantity 로 호출하면 stock 이 그만큼 줄어든다
- [x] quantity 가 stock 과 같으면 호출 결과로 stock 이 0 이 된다
- [x] quantity 가 stock 보다 크면 `INSUFFICIENT_STOCK` 예외가 발생한다 (음수 방지)
- [x] quantity 가 0 이거나 음수면 예외가 발생한다

### 1.3 Brand 애그리거트 (최소 골격)
- [x] Brand 를 blank name 으로 생성하면 예외가 발생한다

### 1.4 ProductSortType
- [ ] `from("latest")` · `from("price_asc")` · `from("likes_desc")` 가 대응 enum 으로 매핑된다
- [ ] `from(null)` 은 `LATEST` 로 해석된다 (기본값)
- [ ] `from(미지원 문자열)` 은 `PRODUCT_BAD_REQUEST` 예외가 발생한다

---

## 2. Phase 2 — Application port (`com.loopers.application.product.port`)

인터페이스만 정의. 테스트 없음.

- `ProductRepository` — `save` · `findById` · `findAll(sort, brandId, page, size)`
- `BrandRepository` — `save` · `findById`

---

## 3. Phase 3 — Application Facade (`com.loopers.application.product.ProductFacade`)

mock 기반 단위 테스트.

### 3.1 `getProductDetail`
- [ ] 존재하는 productId 로 호출하면 `ProductDetailResult` (product 정보 · brand 정보 · likeCount) 가 반환된다
- [ ] 존재하지 않는 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다
- [ ] product 는 존재하나 brand 가 존재하지 않으면 `BRAND_NOT_FOUND` 예외가 발생한다 (데이터 정합성)

### 3.2 `getProductList`
- [ ] sort=`LATEST` 로 조회하면 createdAt 내림차순 결과가 반환된다
- [ ] sort=`PRICE_ASC` 로 조회하면 price 오름차순 결과가 반환된다
- [ ] sort=`LIKES_DESC` 로 조회하면 likeCount 내림차순 결과가 반환된다
- [ ] sort 가 null 이면 `LATEST` 로 해석된다
- [ ] brandId 필터를 지정하면 해당 브랜드의 상품만 반환된다
- [ ] page / size 페이징이 적용된다

---

## 4. Phase 4 — Infrastructure adapter (`com.loopers.infrastructure.product` · `brand`)

Testcontainers 통합 테스트 (`modules:jpa` testFixtures 재사용).

### 4.1 ProductRepositoryImpl
- [ ] save 후 findById 로 동일 도메인 객체가 복원된다
- [ ] findAll(sort=LATEST) 가 createdAt desc 로 정렬된다
- [ ] findAll(sort=PRICE_ASC) 가 price asc 로 정렬된다
- [ ] findAll(sort=LIKES_DESC) 가 likeCount desc 로 정렬된다
- [ ] brandId 필터가 적용된다
- [ ] page / size 페이징이 적용된다

### 4.2 BrandRepositoryImpl
- [ ] save 후 findById 로 동일 객체가 복원된다

---

## 진행 로그

- 2026-05-28: plan 작성, TDD 시작 대기.
