# like 도메인 — TDD 진척 체크리스트

> 본 plan 은 [requirements.md](./requirements.md) 의 UC-1 ~ UC-3 을 포괄한다.
> 인증(헤더 인증·UNAUTHORIZED)은 **interfaces 레이어 책임** — Facade 는 "인증 통과한 호출(userId)" 로만 다룬다.

---

## 0. 스코프 / 결정

### 사용자 결정
- **likeCount SSOT** = `Product.likeCount` 비정규화 컬럼 토글 — 좋아요 등록/취소 시 ±1 트랜잭션 갱신. Like 행 `COUNT(*)` 집계는 사용하지 않는다.
- **likedByMe 연동** = 본 작업에 포함. `ProductFacade.getProductDetail` 이 `LikeRepository.existsByUserIdAndProductId` 로 실제 lookup. 시그니처 `loginId: String?` → `userId: Long?` 변경 (인증된 회원 식별 책임은 interfaces).
- **좋아요 취소** = **Hard delete** — `(userId, productId)` 행 자체 제거. Like 에는 soft delete / deletedAt 필드를 두지 않는다.

### 도메인 결정
- `Like(id, userId, productId, likedAt)` — surrogate id + `(userId, productId)` unique 제약.
- `likedAt` 은 도메인 객체의 필드 (`LocalDateTime`). infra 측 `BaseEntity.createdAt` 으로 매핑.
- 정렬 기준은 `likedAt desc`. 외부 응답에는 시각 노출 안 함.
- application 의존: `application.like` → `application.product.port` (Product 검증 + likeCount 갱신).

---

## 1. Phase 1 — Domain (`com.loopers.domain.like`)

순수 Kotlin. Spring / JPA / Repository 의존 금지.

### 1.1 Like 생성
- [x] `Like.create(userId, productId)` 가 likedAt 을 now() 로 설정한다
- ~~userId/productId 양수 검증~~ — Facade 가 인증된 회원 식별자 + Product 존재 확인을 선행하므로 무용한 가드. 제거.

### 1.2 LikeErrorType
- [x] `LIKE_FORBIDDEN` (403) 보유. (BAD_REQUEST 는 도메인에서 던질 일이 없어 제거)

### 1.3 Product likeCount 토글 (Tidy First)
- [x] **구조 커밋**: `val likeCount: Long` → `var likeCount: Long; private set` 변경 (기존 테스트 모두 통과)
- [x] **행위 커밋**: `increaseLikeCount()` 호출 시 likeCount 가 1 증가한다
- [x] **행위 커밋**: `decreaseLikeCount()` 호출 시 likeCount 가 1 감소한다
- [x] **행위 커밋**: likeCount 가 0 일 때 `decreaseLikeCount()` 는 0 유지 (음수 방지 멱등)

---

## 2. Phase 2 — Application port

`application.like.port.LikeRepository`
- `save(like): Like`
- `findByUserIdAndProductId(userId, productId): Like?`
- `existsByUserIdAndProductId(userId, productId): Boolean`
- `delete(like)`
- `findAllByUserId(userId, page, size): List<Like>` — likedAt desc 고정

---

## 3. Phase 3 — Application Facade

mock 기반 단위 테스트. `LikeFacade` 가 `LikeRepository` + `ProductRepository` 의존.

### 3.A UC-1. `like(userId, productId)` — 멱등 등록
- [x] 존재하는 productId 로 신규 호출하면 Like 가 저장되고 `Product.likeCount` 가 1 증가한다
- [x] 이미 좋아요한 상태에서 재호출하면 추가 저장 없이 멱등 통과한다 (likeCount 변동 없음)
- [x] 존재하지 않는 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다

### 3.B UC-2. `unlike(userId, productId)` — 멱등 취소
- [x] 좋아요 행이 있으면 제거되고 `Product.likeCount` 가 1 감소한다
- [x] 좋아요가 없는 상태에서 호출하면 무동작 멱등 통과한다 (likeCount 변동 없음)
- [x] 존재하지 않는 productId 면 `PRODUCT_NOT_FOUND` 예외가 발생한다

### 3.C UC-3. `getMyLikes(authedUserId, requestedUserId, page, size)` — 내 목록
- [x] 본인 식별자로 호출하면 좋아요한 상품 요약 목록이 반환된다
- [x] 좋아요한 상품이 없으면 빈 목록이 반환된다
- [x] 인증된 회원과 다른 식별자로 호출하면 `LIKE_FORBIDDEN` 예외가 발생한다
- [x] page / size 가 Repository 에 전달된다

---

## 4. Phase 4 — Infrastructure adapter

Testcontainers 통합 테스트 (`@DataJpaTest` + `MySqlTestContainersConfig`).

### 4.1 LikeRepositoryImpl
- [x] save 후 findByUserIdAndProductId 로 동일 도메인 객체가 복원된다
- [x] `(userId, productId)` 동일한 두 번째 save 는 unique 제약으로 실패한다
- [x] existsByUserIdAndProductId 가 정확히 판정한다
- [x] delete 가 행을 제거한다 (이후 findByUserIdAndProductId 가 null)
- [x] findAllByUserId 가 likedAt desc 로 정렬된다
- [x] findAllByUserId 에 page / size 페이징이 적용된다
- [x] 다른 user 의 좋아요는 결과에 포함되지 않는다

---

## 5. Phase 5 — Product 통합

### 5.1 ProductFacade likedByMe 연동 (구조 → 행위)
- [x] **구조 커밋**: `getProductDetail(productId, loginId: String?)` → `getProductDetail(productId, userId: Long?)` 시그니처 변경. 기존 테스트 갱신.
- [x] **행위 커밋**: userId 가 null 이면 likedByMe=false 로 응답한다
- [x] **행위 커밋**: userId 가 주어지면 `LikeRepository.existsByUserIdAndProductId` 결과를 likedByMe 로 응답한다

### 5.2 ProductEntity likeCount 동기화
- [x] `ProductEntity.syncFrom` 이 도메인의 likeCount 변경을 entity 컬럼에 반영한다 (Phase 4 ProductRepositoryImpl 통합에서 검증)

---

## 진행 로그

- 2026-05-29: plan 작성. likeCount SSOT = Product 컬럼 토글, likedByMe 실제 연동 포함, 취소 = hard delete 결정.
- 2026-05-29: Phase 1 ~ 5 전체 Green. Like 도메인 모델 · LikeFacade (10 tests) · LikeRepositoryImpl (7 통합 tests) · Product likeCount 토글 (3 tests) · ProductFacade.getProductDetail likedByMe 연동 (2 tests). 사용자 피드백 — Like 의 userId/productId 양수 검증은 무용해서 제거, LIKE_BAD_REQUEST 도 함께 제거.
