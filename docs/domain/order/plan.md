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
- 3금액(originalAmount/discountAmount/totalAmount) 노출 명명 확정 (현재 originalAmount/totalAmount 게터).
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

## 3. Phase 3 — 3금액 노출 정합 — ✅ (2026-06-10)

> **명명 결정**: 코드 어휘로 통일. 도메인 getter `originalAmount`(적용 전)·`discountAmount`·`totalAmount`(최종) 를 **그대로 유지**(gross/payable 로 개명하지 않음). v0.4 문서(api-spec·requirements §1)에 임의로 넣었던 `grossAmount`/`payableAmount` 는 `originalAmount`/`totalAmount` 로 정정.

### 3.1 Order 금액 노출
- [x] 도메인 getter `originalAmount`/`totalAmount` 유지 — 개명 없음
- [x] `OrderResult`/`AdminOrderResult` 가 3금액(`originalAmount`·`discountAmount`·`totalAmount`) 모두 노출 (`originalAmount` 신규 추가)
- [x] 3금액은 라인 스냅샷 + 저장된 `discountAmount` 에서 파생 — **OrderEntity 에 별도 gross/payable 컬럼 추가하지 않음**(중복 비정규화 회피, 불변 보장은 라인+할인 스냅샷으로 충족)
- [x] api-spec §0.6·예시, requirements §1 유비쿼터스 언어를 코드 어휘로 정정
- [x] OrderCouponIntegrationTest 에 `originalAmount` 단언 추가, 컴파일·order 테스트·ktlint 그린

---

## 4. Phase 4 — PaymentGateway 도입 (커밋 후 결제, 이벤트 방식) — ✅ (2026-06-10)

> **구조 결정 = 도메인 이벤트 + AFTER_COMMIT 리스너.** `placeOrder`(tx1) 가 재고·쿠폰·주문 PENDING 저장 후 `OrderPlacedEvent` 발행 → 커밋 → `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` 가 PG 호출·markPaid·저장(tx2). 외부 호출이 주문 트랜잭션 밖.
> **생성 응답은 `PAYMENT_PENDING`** (응답값이 리스너 실행 전에 조립됨 — 리스너가 응답을 주지 못하는 구조). 리스너는 커밋 직후 동기 실행되어 DB 는 즉시 `PAID`, 이후 조회는 `PAID`.

### 4.1 port / VO / adapter
- [x] `application.order.port.PaymentGateway` — `charge(orderId, amount): PaymentResult`
- [x] `PaymentResult(transactionId, resultCode, success)` VO (애그리거트 아님)
- [x] `infrastructure.order.AlwaysSuccessPaymentGateway` — `tx-$orderId` + `APPROVED`

### 4.2 이벤트 흐름
- [x] `application.order.OrderPlacedEvent(orderId)` + `OrderFacade` 가 새 주문 저장 후 `ApplicationEventPublisher` 로 발행 (멱등 재요청·검증 실패 시 미발행)
- [x] `application.order.OrderPaymentEventListener` — AFTER_COMMIT + REQUIRES_NEW, PG 호출 → 성공 시 `markPaid` / 실패 시 `markPaymentFailed`(항상 성공이라 미발火) → save
- [x] 정상(통합): `placeOrder` 응답 `PAYMENT_PENDING`, 커밋 후 리스너가 주문을 `PAID` + `paymentTransactionId` 박음 (OrderCouponIntegrationTest 검증)
- [x] 멱등(단위): 재요청 시 기존 주문 반환, 이벤트 미발행(`verify(exactly=0)`)
- [x] 컴파일·OrderFacadeTest·OrderCouponIntegrationTest(동시성 포함)·ktlint 그린

---

## 5. Phase 5 — interfaces (행위 — E2E)

> 산출물: `OrderV1Controller`(`/api/v1/orders`) · `OrderV1AdminController`(`/api-admin/v1/orders`) · `*ApiSpec` · DTO. `@RequireAuth`+`@LoginUser`(회원, `AuthUser.loginId` 를 Facade 에 전달), `AdminAuthInterceptor`(어드민, 경로 강제). 와이어 필드 `userCouponId`, 멱등 키 `Idempotency-Key` 헤더(미존재 시 빈 문자열 → 도메인이 `IDEMPOTENCY_KEY_BLANK`).
>
> **페이징 = PageResult (coupon 일관) ✅ 사전 완료 (2026-06-10).** `OrderRepository.findAllByUserIdInPeriod`/`findAllForAdmin` 과 `OrderFacade.getMyOrders`/`getOrdersForAdmin` 을 `List` → `PageResult` 로 변경(`OrderJpaRepository` 가 `Page` 반환 + 기간 쿼리에 `countQuery`, `Page.toPageResult()` 확장). 목록 응답 DTO 는 coupon 처럼 `content`/`page`/`size`/`totalElements`/`totalPages`. (OrderFacadeTest·OrderRepositoryImplIntegrationTest 갱신, 그린.)

### 회원 채널 ✅ (2026-06-10) — `OrderV1Controller`·`OrderV1ApiSpec`·`OrderV1Dto`·`OrderV1ApiE2ETest`
- [x] UC-1 `POST /api/v1/orders` — 정상(쿠폰 적용→PENDING→커밋후 PAID 전이/미적용) / IDEMPOTENCY_KEY_BLANK / EMPTY_LINES / ALREADY_USED_COUPON / 멱등 수렴 / UNAUTHORIZED (E2E)
- [x] UC-2 `GET /api/v1/orders` — 목록(PageResult)·INVALID_DATE_RANGE (getMyOrders 에 기간 검증 가드 추가 + 단위 테스트)
- [x] UC-3 `GET /api/v1/orders/{orderId}` — 본인 상세(PAID) / ORDER_NOT_FOUND / ORDER_FORBIDDEN(타인)
### 어드민 채널 ✅ (2026-06-10) — `OrderV1AdminController`·`OrderV1AdminApiSpec`·`OrderV1AdminApiE2ETest` (`X-Loopers-Ldap`, `AdminAuthInterceptor` 경로 커버)
- [x] UC-4 `GET /api-admin/v1/orders` — 운영 메타(마스킹 표시명·PAID·결제 메타) 포함 PageResult / `X-Loopers-Ldap` 누락 → `401`
- [x] UC-5 `GET /api-admin/v1/orders/{orderId}` — 어느 회원 주문이든 조회 / `ORDER_NOT_FOUND`

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
