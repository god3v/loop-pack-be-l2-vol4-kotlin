# payment 도메인 — TDD 플랜

> **입력 명세**: [requirements.md (v0.1)](./requirements.md) · [api-spec.md](./api-spec.md)
> **방법론**: [Kent Beck TDD & Tidy First](../../guideline/tdd-guideline.md)
> **시작일**: 2026-06-23
> 본 plan 은 Round 6 — **동기·항상성공 mock 결제를 비동기 실(real) PG 연동으로 전환하고, 그 위에 Timeout · CircuitBreaker · Fallback · 폴링 복구를 입히는** 변경을 포괄한다.

---

## 사용 규칙

- **한 줄 = 하나의 실패 테스트 = 하나의 Red → Green → Refactor 사이클**
- **단순한 것 → 복잡한 것** 순으로 배치 (Beck 의 "simplest test first")
- **행위/시나리오**를 적는다 — 구현 디테일이 아닌, 사용자/도메인이 관찰 가능한 행위로 서술
- 작업 중 떠오른 케이스는 즉시 plan.md 에 추가 (살아있는 문서)

### 진척 표시 규약
| 마크 | 의미 |
|---|---|
| `- [ ]` | 미시작 |
| `- [~]` | 진행 중 (Red 만 작성, Green 미완) |
| `- [x]` | 완료 (Green + 필요 시 Refactor 까지) |

### "go" 워크플로우
1. 사용자가 **"go"** → 위에서부터 첫 번째 `- [ ]` 항목 1개를 찾는다
2. 실패 테스트(Red) 작성 → 최소 구현(Green) → 필요 시 구조 정리(Refactor)
3. 사용자 확인 후 체크박스 갱신 + 커밋 (구조/행위 분리)
4. 다음 "go" 까지 대기

---

## 0. 스코프 / 결정 (requirements QnA 합의 — 2026-06-23)

- **진입점 = 명시 API 일원화**: `OrderPlacedEvent` 자동 결제 트리거를 제거하고, 회원이 `POST /api/v1/payments`(orderId·cardType·cardNo) 로 명시 결제한다.
- **주 경로 = 콜백 / 안전망 = 폴링**: 정상은 콜백(§2)으로 확정, 콜백 유실·타임아웃은 폴링 복구(§3)로 수렴.
- **Fallback = 결과 유형으로 구분**: 영구 실패(잘못된 카드·한도 초과)는 즉시 `FAILED`+보상, 일시 장애(통신 실패·타임아웃·CB Open)는 `REQUESTED` 유지 후 복구. 이 분기는 **정산 시점**에 결정되고, 결제 요청 응답은 두 경우 모두 `REQUESTED` 다.
- **스코프 = 결제 전 생애주기 풀 스펙**: 요청·승인·실패·취소/환불을 payment 도메인이 소유. order 는 결제대기 주문 생성까지로 축소(order/requirements.md v0.6).

### 현재 코드 상태 (비동기 PG 로 전환 중)
- `domain.payment` — `Payment`(orderId·amount·status·transactionId·failureReason·시각) + `PaymentStatus(REQUESTED/APPROVED/FAILED/CANCELED)`. `approve`·`fail`(멱등)·`cancel`(멱등)·`accept`(거래 식별자 접수) 상태 전이 **구현·테스트 완료**.
- `domain.order` — `OrderStatus` 에 `CREATED`(생성 초기) 추가. 주문 생명주기: **CREATED(생성) → markPaymentPending → PAYMENT_PENDING(결제 진행) → PAID/PAYMENT_FAILED**. `Order.validatePayable()`(CREATED 만 결제 가능)·`markPaymentPending()`(CREATED→PAYMENT_PENDING) 추가 — 결제 가능 판정·전이를 주문 도메인이 캡슐화(Tell-Don't-Ask).
- `application.payment` — `PaymentFacade` 단일 진입점이 요청·정산을 모두 소유한다. `pay(command)`(`@Transactional`: 주문 잠금 조회 → `order.validateOwnedBy`/`validatePayable`/`markPaymentPending` → 비동기 PG `request` → 거래 식별자 `accept`+save → `PaymentRequestResult` 반환) · `settle(transaction)`(`@Transactional`: `findByTransactionIdForUpdate` 비관 락 → 멱등 가드 → SUCCESS/FAILED/PENDING 분기). (`PaymentInitiator`·`PaymentRequestFacade`·`PaymentCanceler`·`PaymentSettler` 제거.)
- ~~`application.order.port.PaymentGateway`(charge) · `AlwaysSuccessPaymentGateway`~~ **제거됨** — 비동기 PG 포트 `application.payment.port.PaymentGateway`(`request`/`getTransaction`/`getByOrder`) + `PgSimulatorPaymentGateway`(RestClient) 로 대체. ~~`PaymentResult` VO~~ 도 제거 — 정산 입력은 포트의 `PgTransaction` 으로 일원화.
- 트리거 — 자동 결제(`OrderPlacedEvent`→`OrderPaymentEventListener`) 제거됨(커밋 8916f6a). 결제는 **명시 API(`pay(command)`)** 로만 진입.
- `apps/pg-simulator` — 비동기 결제 API(요청/조회/주문별 조회) + 콜백 통지.

### 갭
- 외부 요청 → 정산 **즉시 확정 분리** — 외부 `request` 는 거래 식별자 접수(REQUESTED)까지, 확정은 콜백/폴링.
- `PaymentGateway` 계약 확장 — `cardType`·`cardNo`·`callbackUrl` 입력, 상태 조회(`getTransaction`/`getByOrder`) 추가, 항상성공 mock 을 실 HTTP 어댑터로 교체.
- 결제 진입점 `POST /api/v1/payments` 컨트롤러 추가 + 자동 이벤트 트리거 제거.
- 콜백 수신 엔드포인트 + 폴링/수동 복구 경로.
- Timeout · CircuitBreaker · Fallback 적용 (resilience4j).
- 폴링 대상 조회(`findByTransactionKey`·처리 중 결제 목록).

---

### Phase 1 — 도메인 모델 (`com.loopers.domain.payment`)
> 값 객체·엔티티 불변식, 생성/상태전이 규칙. 순수 Kotlin.
> 상태 전이(approve/fail/cancel)·멱등 규칙은 베이스라인에서 구현·테스트 완료 — 아래는 비동기 전환으로 새로 필요한 행위만 추가한다.

- [x] 결제를 요청하면 거래 식별자 없이 요청(REQUESTED) 상태로 생성된다 (베이스라인 `PaymentTest.createsRequested` 커버)
- [x] 요청 상태 결제에 외부 거래 식별자를 접수 기록할 수 있다 (2026-06-23 — `Payment.accept`)
- [x] 거래 식별자를 받지 못한 결제도(타임아웃 접수 미확인) 요청 상태로 유효하게 존재한다 (2026-06-24 — `Payment.hasTransactionId()`, 복구 분기 predicate)
- [x] 한 번 접수된 거래 식별자는 같은 결제에서 다른 값으로 덮어쓰이지 않는다 (2026-06-24 — `accept` 가드: 같은 값 멱등 / 다른 값 `TRANSACTION_ID_CONFLICT`)

### Phase 2 — 도메인 서비스 (`com.loopers.domain.payment.{Aggregate}Service`)
> 규칙 조합 + Repository 인터페이스를 통한 상태 변경.
> payment 는 별도 도메인 서비스를 두지 않는다 — 결제 조율(요청·정산·복구·취소)은 `application.payment` 가 트랜잭션 경계와 함께 소유한다(Phase 4). 본 Phase 는 비워 둔다.

### Phase 3 — 인프라 어댑터 (`com.loopers.infrastructure.payment`)
> JPA 매핑·Repository 구현(testcontainers MySQL), 외부 PG HTTP 어댑터(mock 서버).

**Repository**
- [x] 거래 식별자로 결제를 조회할 수 있다 (2026-06-23 — `findByTransactionId`)
- [x] 처리 중(REQUESTED) 결제 목록을 조회할 수 있다 (폴링 복구 대상) (2026-06-23 — `findAllByStatus`)

**외부 PG HTTP 어댑터 (pg-simulator 계약)**
- [x] 결제를 요청하면 외부 PG 가 발급한 거래 식별자와 처리 중 상태를 받는다 (2026-06-23 — `PgSimulatorPaymentGateway.request`, RestClient + MockRestServiceServer)
- [x] 거래 식별자로 외부 결제 상태(성공/실패/처리중)를 조회할 수 있다 (2026-06-23 — `PgSimulatorPaymentGateway.getTransaction`)
- [x] 주문 식별자로 외부 결제건을 조회할 수 있다 (거래 식별자 미확보 복구용) (2026-06-23 — `PgSimulatorPaymentGateway.getByOrder`)
- ~~승인된 결제의 환불을 외부 PG 에 요청한다~~ — **보류**: pg-simulator 에 환불/취소 API 없음 (아래 취소·환불 참고)
- [ ] (보류) 외부 응답이 정한 시간을 넘기면 호출이 타임아웃으로 종료되고 스레드를 무한 점유하지 않는다
- [ ] (보류) 외부 통신 실패·타임아웃이 애플리케이션 예외로 변환되어 외부 예외가 누수되지 않는다

**CircuitBreaker / Timeout — 보류 (API 정상 응답 경로 완성 후 처리)**
> Timeout · 예외 변환 · CircuitBreaker 는 결제 요청 API 의 정상 응답 경로(Phase 4·5 happy path)를 끝낸 뒤 적용한다 (2026-06-23 결정).
- [ ] (보류) 외부 PG 가 반복 실패하면 회로가 열려(Open) 이후 호출이 외부를 거치지 않고 차단된다
- [ ] (보류) 회로가 열린 동안의 호출은 외부를 호출하지 않고 Fallback 으로 처리된다
- [ ] (보류) 느린 응답(slow call)이 임계치를 넘으면 실패로 간주되어 회로 차단에 반영된다

### Phase 4 — Application Facade (`com.loopers.application.payment`)
> 유스케이스 진입점, 트랜잭션 경계, 예외 전파. mock 기반 단위 + 일부 통합.

> **설계 전환(2026-06-24)**: `pay(command)` 는 단일 `@Transactional` — 주문 잠금 조회 → `validatePayable`/`markPaymentPending` → PG `request` → `accept`+save. (구 "REQUESTED 선커밋(REQUIRES_NEW) 후 락 밖 호출" 폐기. 외부 호출이 트랜잭션 안에 들어옴 — 복구 보장은 아래 미해결로.)

- [x] 결제대기 주문에 결제를 요청하면 결제가 요청 상태로 생성되고 접수 정보를 반환한다 (2026-06-24 — `PaymentFacade.pay`, `PaymentRequestResult`)
- [x] 결제 요청이 접수되면 외부 거래 식별자가 결제에 기록된다 (2026-06-24 — `accept(transactionKey)` + save)
- [x] 결제 가능하지 않은 주문(이미 결제 진행·완료·실패·취소)에 결제를 요청하면 결제 불가 예외가 발생한다 (2026-06-24 — `Order.validatePayable` → `ORDER_NOT_PAYABLE`)
- [x] 같은 주문에 동시/중복 결제 요청이 와도 한 번만 결제 진행으로 전이된다 (2026-06-24 — 주문 잠금 + `markPaymentPending`(CREATED→PENDING), 두 번째는 `validatePayable` 거부)
- [x] 타인 소유 주문에 결제를 요청하면 권한 예외가 발생한다 (2026-06-24 — `Order.validateOwnedBy()`, pay 에서 404→403→409 순서로 검증, `ORDER_FORBIDDEN`)
- [ ] (보류) 외부 호출이 타임아웃·통신 실패·회로 차단이면 결제는 요청 상태로 남고 즉시 접수 응답이 반환된다
- [ ] **TBD(설계 전환 여파)**: PG 호출이 `@Transactional` 안에 있어, 호출 중 크래시/롤백 시 결제 레코드가 남지 않는다 — "타임아웃됐는데 PG 성공" 복구(요구사항 §4 선커밋 보장)와 상충. 외부 호출의 트랜잭션 격리 재검토 필요.

**정산 (콜백·폴링 공통)**
> **설계 전환(2026-06-24)**: 정산은 `PaymentFacade.settle(transaction: PgTransaction)` 가 소유한다 — `transactionKey` 로 결제를 찾아 `PgTransactionStatus`(SUCCESS/FAILED/PENDING) 로 승인/실패/미확정 분기. 구 `PaymentSettler.settle(paymentId, PaymentResult)`(동기 boolean) 와 `PaymentResult` VO 는 **제거 완료**(2026-06-24, 행위 이관 후 구조 정리). 보상 실패 롤백 검증(`OrderCompensationFailureIntegrationTest`)도 `PaymentFacade.settle` 로 이관.
- [x] 외부 결과가 성공이면 결제가 승인되고 그 주문이 결제완료로 전이된다 (2026-06-24 — `PaymentFacade.settle` SUCCESS 분기)
- [x] 외부 결과가 실패면 결제가 실패하고 재고·쿠폰이 보상되며 그 주문이 결제실패로 전이된다 (2026-06-24 — `settle` FAILED 분기: `payment.fail` + `orderCompensator.restore` + `order.markPaymentFailed`)
- [x] 이미 정산된(승인·실패·취소) 결제에 결과를 다시 반영하면 멱등하게 무시된다 (2026-06-24 — `settle` 가드 `if (!payment.isRequested()) return`, 주문 조회·저장·보상 모두 건너뜀)
- [x] 같은 결과가 콜백·폴링으로 두 번 도착해도 정산은 한 번만 일어난다 (2026-06-24 — `findByTransactionIdForUpdate` 비관 락 + 멱등 가드, `PaymentSettleConcurrencyIntegrationTest` 로 직렬화 검증)
- [x] 알 수 없는 거래 식별자의 결과 통지는 정산 없이 무시된다 (2026-06-24 — `findByTransactionIdForUpdate(...) ?: return` 가드, 동작 고정 테스트로 확인)

**상태 복구 (폴링·수동)**
- [x] 처리 중 결제를 복구 트리거하면 외부 상태를 조회해 확정 시 정산한다 (2026-06-24 — `PaymentFacade.sync`, 거래 식별자 기반 `getTransaction` → `settle`)
- [ ] 거래 식별자가 없는 처리 중 결제는 주문 식별자로 외부 결제건을 조회해 정산한다 (타임아웃 성공 수렴) — `getByOrder` 기반 복구 미구현(`sync` 는 거래 식별자 미확보 시 미확정 반환)
- [~] 외부가 아직 처리 중이거나 외부 조회가 실패하면 상태를 바꾸지 않고 미확정으로 둔다 — PENDING 은 `settled=false` 로 커버(2026-06-24). 외부 조회 실패(통신 오류) graceful 처리는 예외 변환(보류)과 함께 미구현

**취소·환불 — 보류 (pg-simulator 환불 API 없음)**
> pg-simulator 엔드포인트는 생성·조회뿐 — 환불/취소 API 가 없어 외부 환불이 불가능하다. 외부 `refund` + 취소 오케스트레이션(`application.order.port.PaymentGateway.refund`·`AlwaysSuccessPaymentGateway.refund`·`PaymentFacade.cancel`·`PaymentCanceler` + 관련 테스트)을 제거했다(2026-06-23). 순수 도메인 전이 `Payment.cancel()`·`Order.cancel()` 는 향후 환불 가능 PG 대비 보존(현재 애플리케이션 미참조). 환불 가능 PG 연동 시 재도입한다.

### Phase 5 — Controller E2E (`com.loopers.interfaces.api.payment`)
> HTTP 계약. `ApiResponse` + `ApiControllerAdvice` 표준 응답.

**결제 요청 — `POST /api/v1/payments`**
- [x] 인증 회원이 결제대기 주문에 결제를 요청하면 200 과 함께 요청(REQUESTED) 접수 응답을 받는다 (2026-06-24 — `PaymentV1Controller.pay`, E2E `PaymentV1ApiE2ETest.acceptsPayment`)
- [x] 응답 본문에 카드 번호가 노출되지 않는다 (2026-06-24 — `PayResponse` 에 cardNo 부재, raw 본문 검증)
- [x] 카드 번호 형식이 틀리면 400 BAD_REQUEST 를 받는다 (2026-06-24 — `CardNumber` VO, `INVALID_CARD_NUMBER`)
- [x] 지원하지 않는 카드 종류면 400 BAD_REQUEST 를 받는다 (2026-06-24 — `CardType.from`, `UNSUPPORTED_CARD_TYPE`)
- [x] 인증 헤더가 없으면 401 UNAUTHORIZED 를 받는다 (2026-06-24 — `@RequireAuth` + `AuthInterceptor`)
- [x] 존재하지 않는 주문이면 404 ORDER_NOT_FOUND 를 받는다 (2026-06-24 — facade `ORDER_NOT_FOUND`)
- [x] 타인 소유 주문이면 403 ORDER_FORBIDDEN 을 받는다 (2026-06-24 — `validateOwnedBy` → `ORDER_FORBIDDEN`)
- [x] 결제대기가 아닌 주문이면 409 ORDER_NOT_PAYABLE 을 받는다 (2026-06-24 — `validatePayable` → `ORDER_NOT_PAYABLE`)
- [ ] (보류) 외부 PG 가 장애·지연이어도 결제 요청 API 는 스레드 점유 없이 즉시 200(처리 중)을 응답한다 — Fallback/Timeout(resilience4j) 보류 항목과 묶여 미구현

**콜백 — `POST /api/v1/payments/callback`** (2026-06-24 — `PaymentV1Controller.callback` → `settle`, 회원 인증 없음)
- [x] 성공 콜백을 받으면 200 을 응답하고 해당 주문이 결제완료가 된다
- [x] 실패 콜백을 받으면 200 을 응답하고 해당 주문이 결제실패가 되며 재고·쿠폰이 보상된다
- [x] 같은 콜백을 두 번 받아도 정산은 한 번만 반영된다 (settle 멱등 가드 + 락)
- [x] 알 수 없는 거래 식별자 콜백도 200 으로 수신 확인되고 상태가 변하지 않는다
- [x] 콜백 본문 형식이 틀리면 400 BAD_REQUEST 를 받는다 (`HttpMessageNotReadableException`)

**수동 복구 — `POST /api-admin/v1/payments/{paymentId}/sync`** (2026-06-24 — `PaymentV1AdminController.sync`, `X-Loopers-Ldap` 인증)
- [x] 운영자가 처리 중 결제를 복구 트리거하면 외부 확정 시 정산되고 갱신된 상태를 응답한다 (settled=true·APPROVED)
- [x] 외부가 미확정이면 상태 변화 없이 settled=false 로 응답한다
- [x] 존재하지 않는 결제면 404 PAYMENT_NOT_FOUND 를 받는다
- [x] LDAP 인증에 실패하면 401 UNAUTHORIZED 를 받는다
