# order 도메인 — TDD 진척 체크리스트

> 본 plan 은 [requirements.md](./requirements.md) 의 **UC-1 ~ UC-5 전체** 를 포괄한다.
> 어드민 인증(LDAP·UNAUTHORIZED·FORBIDDEN)은 **interfaces 레이어 책임** — Facade 는 "LDAP 통과한 운영자 호출" 로만 다룬다. (product/like 와 동일 패턴)
> 회원 인증은 `loginId` 헤더 → `UserRepository.findByLoginId` 패턴을 유지한다.

---

## 0. 스코프 / 결정

### 사용자 결정 (재확장 후)
- **Coupon**: 입력 슬롯(`couponId`) 만 수용. 합계 금액 반영 안 함 (Coupon 도메인 미존재 시점의 안전한 플레이스홀더).
- **PaymentGateway**: `application.order.port.PaymentGateway` outbound port + production impl = **항상 성공**. 단위 테스트는 mock 으로 실패 시뮬가능.
- **PAYMENT_FAILED 주문 행**: 잔존. `OrderStatus.PAYMENT_FAILED` 로 저장 — 회원 목록·어드민 목록 모두에 노출 (명세 일관). 재고/쿠폰 효과는 보상으로 원복.
- **본문이 다른 멱등 키 재요청**: 본문 무시 — 기존 응답을 그대로 돌려준다 (명세 TBD 의 합리적 기본값).
- **`결제실패` 주문의 PG 트랜잭션 누락**: 빈 값으로 응답 (명세 UC-4 5a 일관).

### 도메인 결정
- `Order` 에 `status` 필드 도입 — 4 라인 스코프에서 보류하던 결정 해제.
- 보상: `placeOrder` 전체를 `@Transactional` 로 감싸되, **결제 실패는 자연 롤백 대상이 아님** (PAYMENT_FAILED 행을 의도적으로 잔존). 결제 실패 분기는 별도 트랜잭션에서 재고만 원복 후 PAYMENT_FAILED 행을 저장. 가장 단순한 구현: `placeOrder` 내부에서 결제 호출 후 분기 — 실패 시 같은 트랜잭션 안에서 `product.deductStock` 한 결과를 `restoreStock` 으로 되돌리는 명시적 보상 호출.
  - 즉 Product 에 `restoreStock(quantity)` 메서드 추가 필요.
- 회원 표시명: User 도메인의 `maskedName()` 활용 — 없으면 도입 (이름 마지막 1글자 `*` 치환).

### 외부 의존
- `UserRepository.findByLoginId` (회원 인증) · `UserRepository.findById` (어드민 응답의 회원 표시) — 후자는 Phase 2 에서 추가.
- `ProductRepository.findById` · `ProductRepository.save`
- `Product.deductStock(quantity)` — 기존
- `Product.restoreStock(quantity)` — 신규 추가 (PAYMENT_FAILED 보상)

---

## 1. Phase 1 — Domain (`com.loopers.domain.order`)

순수 Kotlin. Spring / JPA / Repository 의존 금지.

### 1.1 ~ 1.4 (완료된 항목)
- [x] `Quantity` · `OrderLine` · `Order.create` 불변식 · `OrderErrorType` (EMPTY_LINES, INVALID_QUANTITY, LINE_BAD_REQUEST)

### 1.5 OrderStatus enum + Order 상태 도입
- [x] OrderStatus enum (PAID, PAYMENT_FAILED) 도입
- [x] Order 에 idempotencyKey · couponId · status · paymentTransactionId · paymentResultCode 필드 추가
- [x] Order.markPaid / markPaymentFailed 메서드
- [x] idempotencyKey blank → IDEMPOTENCY_KEY_BLANK

### 1.6 Product 재고 복원
- [x] Product.restoreStock(quantity) — Stock.restore 위임
- [x] 양수가 아닌 quantity 는 PRODUCT_BAD_REQUEST

### 1.7 OrderErrorType 확장
- [x] IDEMPOTENCY_KEY_BLANK · INVALID_DATE_RANGE · ORDER_NOT_FOUND · ORDER_FORBIDDEN · PAYMENT_FAILED

### 1.8 User maskedName
- [x] User.name() 이 이미 마스킹된 표시명을 반환 (재사용)

---

## 2. Phase 2 — Application port

### 2.1 `OrderRepository`
- `save(order: Order): Order`
- `findById(id: Long): Order?`
- `findByUserIdAndIdempotencyKey(userId, idempotencyKey): Order?` — UC-1 멱등 수렴
- `findAllByUserIdAndOrderedAtBetween(userId, start, end, page, size): List<Order>` — UC-2, orderedAt desc
- `findAllForAdmin(page, size): List<Order>` — UC-4, orderedAt desc

### 2.2 `application.order.port.PaymentGateway`
- `charge(orderId: Long, amount: Int): PaymentResult` — `PaymentResult(transactionId: String?, resultCode: String?, success: Boolean)`

### 2.3 `UserRepository`
- `findById(id: Long): User?` 추가

---

## 3. Phase 3 — Application Facade

mock 기반 단위 테스트.

### 3.A UC-1. `placeOrder(command)` — 회원 주문 생성
- [x] 정상: 각 상품 deductStock 호출 + 결제 호출 + Order(PAID) 저장
- [x] 정상: 라인 스냅샷 (productName, unitPrice) 박힘
- [x] 정상: totalAmount 는 라인 subtotal 합
- [x] 정상: couponId 입력 슬롯만 (합계 영향 없음)
- [x] 멱등: 같은 (userId, idempotencyKey) 재호출 시 기존 OrderResult 반환
- [x] 예외 — loginId 회원 없음 → UNAUTHORIZED
- [x] 예외 — 라인 중 상품 없음 → PRODUCT_NOT_FOUND
- [x] 예외 — 라인 재고 부족 → INSUFFICIENT_STOCK
- [x] 예외 — PG 결제 실패 → Order(PAYMENT_FAILED) 저장 + restoreStock 보상 + PAYMENT_FAILED 예외
- [x] 입력 검증 — idempotencyKey blank → IDEMPOTENCY_KEY_BLANK

### 3.B UC-2. `getMyOrders(loginId, startAt?, endAt?, page, size)` — 내 목록
- [x] 기간 + 페이징 + 본인 userId 가 Repository 에 전달된다
- [x] 기본 윈도우: startAt/endAt 둘 다 null → 최근 30일
- [x] startAt > endAt → INVALID_DATE_RANGE
- [x] loginId 회원 없음 → UNAUTHORIZED

### 3.C UC-3. `getMyOrderDetail(loginId, orderId)` — 회원 단일
- [x] 본인 주문이면 OrderResult 반환
- [x] 존재하지 않으면 ORDER_NOT_FOUND
- [x] 타인 주문이면 ORDER_FORBIDDEN

### 3.D UC-4. `getOrdersForAdmin(page, size)` — 어드민 목록
- [x] 페이징 + 운영 메타 (회원 마스킹 표시명 · PG 트랜잭션ID · 결제 결과 코드) 포함
- [x] 결제실패 + PG 트랜잭션 누락 → 빈 값 응답

### 3.E UC-5. `getOrderForAdmin(orderId)` — 어드민 단일
- [x] 어느 회원 주문이든 AdminOrderResult 반환 (FORBIDDEN 분기 없음)
- [x] 존재하지 않으면 ORDER_NOT_FOUND

---

## 4. Phase 4 — Infrastructure adapter

### 4.1 OrderRepositoryImpl
- [x] save 후 findById 로 라인 스냅샷·status·운영 메타 복원
- [x] findByUserIdAndIdempotencyKey 정확 판정
- [x] findAllByUserIdAndOrderedAtBetween — orderedAt desc + 기간 필터 + 본인 userId
- [x] findAllForAdmin — orderedAt desc + 페이징

### 4.2 PaymentGatewayImpl
- [x] production impl 은 항상 success=true (tx- prefix + APPROVED)

### 4.3 (선택) 보상 통합 검증
- 보류 — 단위 테스트로 보상 흐름 검증 완료. SpringBootTest 추가 비용 대비 가치 낮음 판단.

---

## 진행 로그

- 2026-05-28: 초기 plan 작성 (4 라인 스코프), Phase 1 §1.1 ~ §1.4 닫힘.
- 2026-05-29: 스코프 확장 결정 — UC-1 ~ UC-5 전체. Coupon 입력 슬롯만 / PaymentGateway 항상 성공 / PAYMENT_FAILED 행 잔존 결정. plan 재작성.
- 2026-05-29: UC-1 ~ UC-5 전체 구현 완료. Phase 1 도메인 확장 (status·idempotencyKey·couponId·운영 메타·markPaid/markPaymentFailed·restoreStock) + Phase 2 port (OrderRepository 5 메서드 · PaymentGateway · UserRepository.findById) + Phase 3 OrderFacade 5 메서드 (21 unit tests) + Phase 4 infra (OrderEntity·OrderLineEntity·OrderRepositoryImpl·PaymentGatewayImpl, 4 integration + 1 unit).
