# product 도메인 — TDD 진척 체크리스트

> 본 plan 은 [requirements.md](./requirements.md) 의 UC-1·UC-2(회원 카탈로그) + UC-9~13(관리자 상품) 을 포괄한다.
> 관리자 인증은 **interfaces 레이어 책임** 으로 가정 — Facade 는 "인증 통과한 호출" 로만 다룬다.
> **브랜드(brand) 는 별도 애그리거트 — [brand/plan.md](../brand/plan.md) 로 분리**. Product 는 `brandId` 로 ID 참조한다.

---

## 0. 스코프

- requirements.md 의 UC-1·UC-2 + UC-9~13 (상품 회원 카탈로그 + 관리자 상품)
- `likedByMe` 는 like 도메인 도입 전이므로 **비회원/미인증 시 false 반환만 보장**했고, 이후 like 도메인에서 실제 lookup 으로 연동됨.

### 결정 사항
- `likeCount` 는 `Product` 비정규화 필드 (외부 like 도메인이 갱신).
- Repository 는 기본으로 **soft-deleted 자원을 결과에서 제외** (`@SQLRestriction("deleted_at IS NULL")`). 회원·관리자 모두 동일 (requirements §5).
- SalesStatus 값 집합: `ON_SALE` · `OUT_OF_STOCK` · `OFF_SALE` (전이 규칙 단순 — 자유 전이 허용). 초깃값 `ON_SALE`.
- Facade 는 애그리거트별 단일 — `ProductFacade` (product UC 전체). 회원·관리자 결과 DTO 는 별도.

---

## 1. Phase 1 — Domain (`com.loopers.domain.product`)

순수 Kotlin. Spring / JPA / Repository 의존 금지.

### 1.1 Product 생성 불변식
- [x] Product 를 음수 price 로 생성하면 예외가 발생한다
- [x] Product 를 음수 stock 으로 생성하면 예외가 발생한다
- [x] Product 를 음수 likeCount 로 생성하면 예외가 발생한다
- [x] Product 를 blank name 으로 생성하면 예외가 발생한다

### 1.2 Product 재고 차감 (`deductStock`)
- [x] 양수 quantity 로 호출하면 stock 이 그만큼 줄어든다
- [x] quantity 가 stock 과 같으면 호출 결과로 stock 이 0 이 된다
- [x] quantity 가 stock 보다 크면 `INSUFFICIENT_STOCK` 예외가 발생한다 (음수 방지)
- [x] quantity 가 0 이거나 음수면 예외가 발생한다

### 1.3 ProductSortType
- [x] `from("latest")` · `from("price_asc")` · `from("likes_desc")` 가 대응 enum 으로 매핑된다
- [x] `from(null)` 은 `LATEST` 로 해석된다 (기본값)
- [x] `from(미지원 문자열)` 은 `PRODUCT_BAD_REQUEST` 예외가 발생한다

### 1.4 SalesStatus (enum)
- [x] enum 이 `ON_SALE` · `OUT_OF_STOCK` · `OFF_SALE` 세 값을 보유한다
- [x] `from("on_sale" 등)` 가 대응 enum 으로 매핑된다 (소문자 key)
- [x] `from(미지원 문자열)` 은 `PRODUCT_BAD_REQUEST` 예외가 발생한다

### 1.5 Product soft delete
- [x] `softDelete()` 호출 시 `deletedAt` 이 설정된다
- [x] `softDelete()` 된 Product 는 `isDeleted()` 가 true 다
- [x] 이미 삭제된 Product 의 `softDelete()` 재호출은 멱등이다 (deletedAt 변동 없음)

### 1.6 Product 등록 시 SalesStatus 초깃값
- [x] `Product.create(...)` 시 salesStatus 가 `ON_SALE` 로 기본 설정된다

### 1.7 Product 업데이트 (`update`)
- [x] `update(name, price, salesStatus)` 로 세 필드가 갱신된다
- [x] `update` 인자에 brandId 는 받지 않는다 (등록 이후 불변)

---

## 2. Phase 2 — Application port (`ProductRepository`)

인터페이스만 정의. 테스트 없음 (port impl 은 Phase 4 가 검증).

- `save(product): Product`
- `findById(id): Product?` — soft-deleted 제외
- `findAll(sort, brandId?, page, size): List<Product>` — soft-deleted 제외
- `findAllForAdmin(brandId?, page, size): List<Product>` — soft-deleted 제외, 최신순 고정
- `existsByBrandIdAndName(brandId, name): Boolean` — soft-deleted 제외 (유일성 검증)
- `findAllByBrandId(brandId): List<Product>` — brand UC-6 카스케이드용

---

## 3. Phase 3 — Application Facade (`application.product.ProductFacade`)

mock 기반 단위 테스트.

#### UC-1. `getProducts(sort, brandId?, page, size)` — 회원 카탈로그
- [x] sort=`LATEST` 로 조회하면 Repository 에 동일 sort 가 전달된다 (정렬 결과 검증은 Phase 4)
- [x] sort=`PRICE_ASC` 로 조회하면 Repository 에 동일 sort 가 전달된다
- [x] sort=`LIKES_DESC` 로 조회하면 Repository 에 동일 sort 가 전달된다
- [x] sort 가 null 이면 `LATEST` 로 해석된다
- [x] brandId 필터를 지정하면 Repository 에 동일 필터가 전달된다
- [x] page / size 가 Repository 에 전달된다

#### UC-2. `getProductDetail(productId, loginId?)` — 회원 상세
- [x] 존재하는 productId 로 호출하면 `ProductDetailResult` (product · brand · likeCount · likedByMe=false) 가 반환된다
- [x] 존재하지 않는 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다
- [x] product 는 존재하나 brand 가 없으면 `BRAND_NOT_FOUND` 예외가 발생한다 (정합성)

#### UC-9. `getProductsForAdmin(brandId?, page, size)` — 관리자 목록
- [x] `findAllForAdmin` 으로 위임된다 (정렬·페이징 검증은 Phase 4)
- [x] brandId 필터가 Repository 에 전달된다
- [x] 응답 항목에 salesStatus 가 포함된다

#### UC-10. `getProductForAdmin(productId)` — 관리자 상세
- [x] 존재하는 productId 로 호출하면 `AdminProductDetailResult` (product · brand · salesStatus) 가 반환된다
- [x] 존재하지 않거나 삭제된 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다

#### UC-11. `registerProduct(command)` — 관리자 등록
- [x] 유효한 입력으로 등록하면 salesStatus=`ON_SALE` 의 신규 Product 가 저장된다
- [x] 지정 brandId 가 존재하지 않으면 `BRAND_NOT_FOUND` 예외가 발생한다
- [x] 같은 브랜드 안에 같은 이름이 이미 있으면 `DUPLICATE_PRODUCT_NAME` 예외가 발생한다

#### UC-12. `updateProduct(productId, command)` — 관리자 수정
- [x] 정상 입력으로 수정하면 name / price / salesStatus 가 갱신된다
- [x] 존재하지 않거나 삭제된 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다
- [x] 같은 브랜드에 같은 이름의 다른 상품이 있으면 `DUPLICATE_PRODUCT_NAME` 예외가 발생한다 (자기 자신 제외)
- [x] 이름이 동일하면 중복 검사를 건너뛴다 (자기 자신 제외 보장)

#### UC-13. `deleteProduct(productId)` — 관리자 삭제
- [x] 정상 호출 시 Product 가 soft delete 된다 (저장됨)
- [x] 존재하지 않거나 이미 삭제된 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다

---

## 4. Phase 4 — Infrastructure adapter (`ProductRepositoryImpl`)

Testcontainers 통합 테스트 (`modules:jpa` testFixtures 재사용).

- [x] save 후 findById 로 동일 도메인 객체가 복원된다
- [x] findById 는 soft-deleted Product 를 null 로 반환한다
- [x] findAll(sort=LATEST) 가 createdAt desc 로 정렬된다
- [x] findAll(sort=PRICE_ASC) 가 price asc 로 정렬된다
- [x] findAll(sort=LIKES_DESC) 가 likeCount desc 로 정렬된다
- [x] findAll 은 soft-deleted Product 를 제외한다
- [x] findAll 에 brandId 필터가 적용된다
- [x] findAllForAdmin 이 createdAt desc + soft delete 제외 + brandId 필터를 적용한다
- [x] existsByBrandIdAndName 가 soft-deleted 를 제외하고 판정한다
- [x] findAllByBrandId 가 해당 브랜드의 (soft-deleted 제외) 상품을 모두 반환한다
- [x] page / size 페이징이 적용된다

---

## 5. Phase 5~ — interfaces 계층 (미착수)

> 회원/관리자 상품 컨트롤러·DTO·ApiSpec·E2E + 목록 PageResult 전환. brand 의 Phase 5~9 (관리자 인증 공유 인프라 포함) 완료 후 동일 패턴으로 별도 수립 예정. 관리자 채널 인증(`AdminAuthInterceptor`)은 brand 에서 만든 것을 재사용한다.

- [ ] (예정) 회원 `GET /api/v1/products`, `GET /api/v1/products/{id}` (목록 PageResult, likedByMe)
- [ ] (예정) 관리자 `GET/POST/PUT/DELETE /api-admin/v1/products` (목록·상세·등록·수정·삭제)

---

## 진행 로그

- 2026-05-28: plan 초안 작성, Phase 1 (4 라인 스코프) 11 개 완료.
- 2026-05-29: UC 전체 포함으로 스코프 확장 — plan 재작성.
- 2026-05-29: Phase 2 port 인터페이스 정의 완료 (ProductRepository · BrandRepository).
- 2026-05-29: Phase 3 진입. UC-1 #1 (sort 전달) Green — ProductFacade · ProductSummaryResult · ProductFixture 도입.
- 2026-05-29: Phase 3 전체 (UC-1 ~ UC-13, 34 tests) 완료. ProductFacade · BrandFacade.
- 2026-05-29: Phase 4 infra 어댑터 도입 — Product · Brand 엔티티/JpaRepository/RepositoryImpl. SQLRestriction(deleted_at IS NULL) 으로 soft delete 제외.
- 2026-05-29: Phase 4 통합 테스트 전체 (15 tests) Green. Testcontainers MySQL.
- 2026-06-09: **brand 도메인 분리** — brand 의 Phase 1~4 체크리스트를 [brand/plan.md](../brand/plan.md) 로 이동. 본 plan 은 상품(UC-1·2·9~13) 전용. 상품 interfaces 계층은 brand 이후 별도 수립.
