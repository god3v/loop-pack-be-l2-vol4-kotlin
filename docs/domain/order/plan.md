# order 도메인 — TDD 진척 체크리스트

> 본 plan 은 [requirements.md (v0.4)](./requirements.md) 와 [api-spec.md](./api-spec.md) 의 UC-1 ~ UC-5 를 포괄한다.
> 데이터 모델: [docs/design/04-erd.md](../../design/04-erd.md) 의 `orders` · `order_lines` · `coupons` · `user_coupons`.
> 회원 인증은 `loginId` 헤더 → `UserRepository.findByLoginId`. 어드민 인증(LDAP)은 interfaces 레이어 책임.

---

## 0. 스코프 / 결정 (v0.4 — 설계 리뷰 반영)

### 사용자 결정 (2026-06-10, 코드 리뷰 후)
- **Q1 쿠폰 연동 = A (OrderFacade 직접 조율)**: `OrderFacade` 가 `UserCouponRepository`·`CouponRepository` + 도메인 객체(`UserCoupon.use`·`Coupon.calculateDiscount`·`Coupon.isExpired`) 를 **자신의 트랜잭션 안에서 직접 조율**한다. 현행 `CouponFacade.applyCoupon` 호출(B) 은 제거한다. requirements §5 를 A 에 맞춰 개정 완료(v0.4).
- **Q2 식별자 명명 = 전면 `userCouponId` 개명**: 와이어(JSON) 필드 포함 전부 `couponId` → `userCouponId`. (week4 원 스펙의 `couponId` 와 의도적으로 다름 — api-spec §0.4 에 명시.)
- **Q3 결제 = 최소 PaymentGateway 지금 도입(올바른 형태)**: `application.order.port.PaymentGateway`(outbound) + `PaymentResult` VO + `infrastructure.order` 항상 성공 어댑터. **주문 저장 트랜잭션 커밋 이후** 호출(트랜잭션 안 호출 아님). 흐름: `tx1(재고+쿠폰+주문 PAYMENT_PENDING) → 커밋 → PG(항상 성공) → tx2(markPaid → PAID)`.
- **락 전략 = TBD**: 발급 쿠폰 단일 사용은 Coupon 이 비관 락(`findByIdForUpdate`) 제공(이미 구현). 재고 차감 락(비관/낙관)은 미정(§5).

### 베이스라인 (현재 코드 — 검증 완료)
- **coupon 도메인**: domain(9) · application(`CouponFacade`) · infrastructure(6) 전부 구현됨. `CouponFacade.applyCoupon(userId, userCouponId, orderAmount): Long` 은 `findByIdForUpdate` 비관 락 + 소유/사용/만료 검증 + `calculateDiscount` + `use` + save. `UserCouponRepository.findByIdForUpdate`, `CouponRepository.findByIdIncludingDeleted` 존재.
- **order 도메인**: `Order`(couponId·discountAmount·originalAmount·totalAmount·markPaid/markPaymentFailed·applyCoupon(userCouponId, discountAmount)), `OrderStatus`(PENDING/PAID/FAILED), `OrderService.createOrder`(재고 차감+라인 스냅샷), `OrderFacade`(placeOrder 가 **CouponFacade.applyCoupon 호출(B)**, PG 없음, 주문을 PENDING 으로 저장 후 markPaid 미호출 → PENDING 잔존 드리프트), 5 메서드. infra(OrderEntity 등). 테스트(OrderFacadeTest·OrderTest·OrderRepositoryImplIntegrationTest).
- **interfaces.api.order**: 없음(HTTP 엔드포인트 미존재).

### 갭 (v0.4 에서 닫을 것)
- B(CouponFacade 호출) → **A(직접 조율)**. `CouponFacade.applyCoupon` + 그 테스트 제거, 조율을 OrderFacade 로 이주.
- 와이어/내부 `couponId` → **`userCouponId`** 전면 개명.
- PG 부재 + PENDING 잔존 → **PaymentGateway(커밋 후) + PAID 전이**.
- 3금액(grossAmount/discountAmount/payableAmount) 노출 명명 확정 (현재 originalAmount/totalAmount 게터).
- interfaces 부재 → 컨트롤러·ApiSpec·DTO·E2E.

---

## 1. Phase 1 — 구조 변경 (Tidy First, 행위 무변경) — **별도 커밋**

> 구조 변경과 행위 변경은 같은 커밋에 섞지 않는다 (CLAUDE.md). 먼저 개명만, 빌드/테스트 그린 유지.

### 1.1 couponId → userCouponId 전면 개명 ✅ (2026-06-10)
- [x] `PlaceOrderCommand.couponId` → `userCouponId`
- [x] `Order.couponId` 필드·생성자·`create()` 파라미터 → `userCouponId` (`applyCoupon` 파라미터는 이미 userCouponId)
- [x] `OrderResult.couponId` / `AdminOrderResult.couponId` → `userCouponId` (+ from/of 매핑)
- [x] `OrderEntity.couponId` 필드 → `userCouponId`, `@Column(name = "user_coupon_id")` (운영 데이터 없음 — 컬럼 정렬)
- [x] 테스트(`OrderTest`·`OrderFacadeTest`·`OrderRepositoryImplIntegrationTest`·`OrderCouponIntegrationTest`)의 `couponId` 접근부 갱신 — `UserCoupon.issue(couponId=…)`(쿠폰 도메인 파라미터)는 보존
- [x] 빌드·전체 테스트 그린 확인 (컴파일·단위·통합·ktlint 모두 통과, 행위 변화 없음)

---

## 2. Phase 2 — 쿠폰 직접 조율 전환 (A) — **별도 커밋 (행위)**

> mock 기반 단위 테스트. CouponFacade 호출(B) → 도메인/리포 직접 조율(A).

### 2.1 OrderFacade 의존성 교체 ✅ (2026-06-10)
- [x] 생성자에서 `CouponFacade` 제거, `UserCouponRepository`·`CouponRepository` 주입
- [x] `placeOrder` 안에 쿠폰 조율을 **인라인**(private 메서드 회피): `findByIdForUpdate` → 소유자·`isUsed()`·`isExpired(now)` 검증 → `calculateDiscount(originalAmount)` → `use(now)` → `save` → `order.applyCoupon(...)`
- [x] `CouponRepository.findByIdIncludingDeleted`(소프트삭제 템플릿 사용 허용) 분기·비관 락(`findByIdForUpdate`)·`CouponErrorType` 예외 타입 보존
- [x] 전 과정이 `placeOrder` 단일 `@Transactional` 안 → 후속 실패 시 쿠폰 소진까지 롤백 (OrderCouponIntegrationTest 로 확인)

### 2.2 테스트 이주 ✅ (2026-06-10)
- [x] `OrderFacadeTest` 의 `couponFacade` 목킹을 `UserCouponRepository`·`CouponRepository` 목킹 + `CouponFixture` 도메인 실객체로 교체 (쿠폰 적용·소유자 불일치·이미 사용·만료 시나리오)
- [x] 최소금액 미달 등 e2e 시나리오는 `OrderCouponIntegrationTest`(동시성 단일 소진 포함) 가 커버
- [x] `CouponFacade.applyCoupon` + `CouponFacadeTest` 의 applyCoupon 케이스 제거 (호출처 없음 — order 가 직접 조율)
- [x] 컴파일·OrderFacadeTest·OrderCouponIntegrationTest·CouponFacadeTest·도메인·ktlint 모두 그린

---

## 3. Phase 3 — 3금액 스냅샷 정합 (도메인) — **행위 커밋**

### 3.1 Order 금액 노출
- [ ] `grossAmount`(=상품 합계, 현 originalAmount) · `discountAmount` · `payableAmount`(=현 totalAmount) 명명 정합
- [ ] `OrderResult`/`AdminOrderResult` 가 3금액을 노출 (단일 totalAmount 대체)
- [ ] `OrderEntity` 3금액 컬럼 매핑 + ERD 동기화 (save→findById 복원 통합 테스트)

---

## 4. Phase 4 — PaymentGateway 도입 (커밋 후 결제) — **행위 커밋**

> 흐름: `tx1(재고+쿠폰+주문 PENDING) → 커밋 → PG → tx2(markPaid → PAID)`. 외부 호출은 트랜잭션 밖.

### 4.1 port / VO / adapter
- [ ] `application.order.port.PaymentGateway` — `charge(orderId, amount): PaymentResult`
- [ ] `PaymentResult(transactionId: String, resultCode: String, success: Boolean)` VO (애그리거트 아님)
- [ ] `infrastructure.order` 의 항상 성공 어댑터 (tx-prefix + `APPROVED`)

### 4.2 OrderFacade 2단계 흐름
- [ ] `placeOrder` 를 비-트랜잭션 조율로: ① `@Transactional` 메서드로 재고·쿠폰·주문(PENDING) 저장 → ② 커밋 후 PG.charge → ③ 별도 `@Transactional` 메서드로 markPaid→PAID 저장
- [ ] Spring self-invocation 회피: tx 메서드를 별도 빈(예: `OrderRegistrar`/도메인 서비스 빈)으로 분리하거나 `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` 중 택1 — **설계 결정 필요** (단순함 우선: 별도 빈 2-메서드)
- [ ] 정상: 주문이 PAID + paymentTransactionId/resultCode 박힘
- [ ] 멱등: 재요청 시 기존 주문 반환, PG 재호출 없음
- [ ] ⚠ markPaymentFailed/PAYMENT_FAILED 경로는 항상 성공이라 미발火 — dead 아님, 차주 실제 PG 대비 보존

---

## 5. Phase 5 — interfaces (행위 — E2E)

> 산출물: `OrderV1Controller`(`/api/v1/orders`) · `OrderV1AdminController`(`/api-admin/v1/orders`) · `*ApiSpec` · DTO. 와이어 필드 `userCouponId`, 멱등 키 `Idempotency-Key` 헤더.

- [ ] UC-1 `POST /api/v1/orders` — 정상(쿠폰 적용/미적용) / IDEMPOTENCY_KEY_BLANK / EMPTY_LINES / INVALID_QUANTITY / PRODUCT_NOT_FOUND / INSUFFICIENT_STOCK / USER_COUPON_NOT_FOUND / ALREADY_USED_COUPON / COUPON_NOT_APPLICABLE / UNAUTHORIZED / 멱등 재요청
- [ ] UC-2 `GET /api/v1/orders` — 목록·페이징·기간·INVALID_DATE_RANGE·UNAUTHORIZED
- [ ] UC-3 `GET /api/v1/orders/{orderId}` — 본인/ORDER_NOT_FOUND/ORDER_FORBIDDEN/UNAUTHORIZED
- [ ] UC-4 `GET /api-admin/v1/orders` — 운영 메타(마스킹 표시명·결제 메타)·401/403
- [ ] UC-5 `GET /api-admin/v1/orders/{orderId}` — 어느 회원 주문이든·ORDER_NOT_FOUND

---

## 6. Phase 6 — 동시성 테스트 (락 전략 TBD)

- [ ] 동일 발급 쿠폰 N 동시 주문 → 정확히 1건 성공, 나머지 `ALREADY_USED_COUPON` (Coupon 비관 락 `findByIdForUpdate` 가 직렬화 — 검증)
- [ ] 동일 상품 재고 K 에 N(>K) 동시 주문 → 정확히 K건 성공, 재고 음수 없음
- [ ] **TBD**: 재고 차감 락 — 비관 vs 낙관(`@Version`) vs 조건부 UPDATE 선택·적용

---

## 7. Phase 7 — 문서 동기화

- [x] requirements v0.4 — A 조율 주체 명시, userCouponId 전면, PG 커밋 후 2단계 (2026-06-10)
- [x] api-spec — userCouponId 와이어, 상태/결제 메타, PG 부록 (2026-06-10)
- [x] plan v0.4 — A/전면개명/PG + Tidy-First 순서 (2026-06-10)
- [ ] 구현 후 코드 ↔ api-spec 정합 재확인, ERD 3금액·user_coupon_id·결제 메타 컬럼 반영

---

## 진행 로그

- 2026-05-28 ~ 05-29: 초기 plan, UC-1~5 베이스라인 구현 (당시 PaymentGateway 드리프트).
- 2026-06-09: v0.3 — 쿠폰 사용 흡수·단일 트랜잭션 롤백·3금액(문서만). (당시 Q3=직접 조율 메모, PG 차주 이관.)
- 2026-06-10: **v0.4 — 코드 리뷰 후 재결정.** 다관점 워크플로(7 에이전트) + 적대적 검증으로 (1) 쿠폰 연동 A 확정(requirements §5 개정), (2) userCouponId 전면 개명, (3) 최소 PG 커밋 후 도입 결정. Tidy-First 구현 순서 수립(구조 개명 → A 전환 → 3금액 → PG → interfaces → 동시성).
