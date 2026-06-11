# coupon 도메인 — TDD 진척 체크리스트

> 본 plan 은 [requirements.md](./requirements.md) 의 UC-1·UC-2(회원) + UC-3~8(관리자) + UC-9(쿠폰 사용 — 도메인 능력) 을 포괄한다.
> 데이터 모델: [docs/design/04-erd.md](../../design/04-erd.md) 의 `coupons` · `user_coupons`.
> 관리자 인증은 **interfaces 레이어 책임** — `AdminAuthInterceptor`(`/api-admin/**` 경로 인증) 를 재사용한다. Facade 는 "인증 통과한 호출" 로만 다룬다.
> 회원 인증은 `@RequireAuth` + `@LoginUser` 를 따른다.

---

## 0. 스코프

- requirements.md 의 UC-1~UC-9 전부.
- **2 애그리거트**: `Coupon`(템플릿, 관리자 CRUD) + `UserCoupon`(발급 인스턴스, 회원 발급·조회·사용). `UserCoupon` 은 `couponId` 로 템플릿을 ID 참조한다.
- 쿠폰 **사용(UC-9)** 의 도메인 능력은 `UserCoupon.use`(단일 사용·사용 가능 기간) + `Coupon.calculateDiscount`(최소금액·할인) 가 소유한다. 결선은 `OrderFacade.placeOrder`(Phase 9) 가 쿠폰 port 를 직접 주입받아 같은 트랜잭션에서 조율한다 — 별도 `CouponFacade.applyCoupon` 진입점은 두지 않는다(Facade→Facade 금지). 결제 실패 보상은 결제 단계 도입 시 다룬다.

### 결정 사항
- **발급 1인 1매**: `(user_id, coupon_id)` UNIQUE. 재발급 → `409 ALREADY_ISSUED_COUPON`. 동시 발급의 유니크 위반도 같은 에러로 흡수.
- **사용 최대 1회**: `UserCoupon.use()` 는 `AVAILABLE → USED` 단방향. 이미 `USED` 면 `409 ALREADY_USED_COUPON`. 동시성은 통합 시 행 락/조건부 갱신으로 보장(범위 메모).
- **시간 모델(v0.3)**: 템플릿은 발급 가능 구간(`issueStartAt`~`issueEndAt`)·사용 가능 구간(`useStartAt`~`useEndAt`) 을 가진다. 발급 시 사용 가능 구간을 발급 쿠폰(`usableFrom`~`expiredAt`) 으로 **스냅샷**한다.
- **만료 파생**: 저장 상태는 `AVAILABLE`/`USED` 둘뿐. `EXPIRED` 는 발급 쿠폰 자신의 `expiredAt`(스냅샷) 경과로 조회·사용 시 파생(템플릿 무관). 만료 배치 없음.
- **할인 계산**: `FIXED` → `min(value, orderAmount)`. `RATE` → `floor(orderAmount × value / 100)`, 주문 합계 상한. 둘 다 음수 불가.
- **정책 다형 모델**: `CouponName`(공백 불가) VO + `DiscountPolicy` sealed interface(`Fixed(amount)`/`Rate(percent)`, 각자 불변식·`discountFor(orderAmount)` 소유). `DiscountType` enum(`FIXED`/`RATE`) 은 영속/와이어 discriminator 로 유지하고, `(type, value) ↔ 정책` 양방향 매핑은 `DiscountPolicy.type`/`value`/`of` 가 단일 소유한다(타입코드 when 은 복원 1지점에만, 컴파일 exhaustive). *(v0.2 에서 `Discount(type, value)` VO 를 정책 모델로 구조 리팩터)*
- **템플릿 삭제 무영향**: `Coupon` 은 soft delete. `CouponEntity` 는 `@SQLRestriction` 대신 **리포지토리에서 명시적 `deletedAt IS NULL` 필터** — 발급 쿠폰 조회·사용 경로는 삭제 마크를 무시하고 템플릿을 조회해야 하기 때문(`findByIdIncludingDeleted`/`findAllByIdsIncludingDeleted`).
- **상태 직렬화**: `type`/`status` 와이어 값은 enum 이름(대문자) 그대로. (product `salesStatus` 의 snake_case key 와 다름 — 원 스펙 `"type":"RATE"` 준수.)
- **Facade 단일**: `CouponFacade` (쿠폰 UC 전체). 회원·관리자 결과 DTO 는 별도.

---

## 1. Phase 1 — Domain (`com.loopers.domain.coupon`)

순수 Kotlin. Spring / JPA / Repository 의존 금지.

### 1.1 DiscountType (enum)
- [x] enum 이 `FIXED` · `RATE` 두 값을 보유한다
- [x] `from("FIXED")` · `from("RATE")` 가 대응 enum 으로 매핑된다
- [x] `from(미지원 문자열)` 은 `COUPON_BAD_REQUEST` 예외가 발생한다

### 1.2 Discount (VO)
- [x] `Discount(FIXED, value)` 를 0 이하 value 로 생성하면 `COUPON_BAD_REQUEST` 예외가 발생한다
- [x] `Discount(RATE, value)` 를 1 미만 또는 100 초과로 생성하면 `COUPON_BAD_REQUEST` 예외가 발생한다
- [x] `FIXED` 의 `amountFor(orderAmount)` 는 `min(value, orderAmount)` 다 (value > orderAmount 면 orderAmount)
- [x] `RATE` 의 `amountFor(orderAmount)` 는 `floor(orderAmount × value / 100)` 다 (버림)
- [x] `RATE` 의 `amountFor` 결과는 orderAmount 를 초과하지 않는다 (상한)
- [x] 두 종류 모두 `amountFor` 결과는 음수가 아니다

### 1.3 CouponName (VO)
- [x] blank 이름으로 생성하면 `COUPON_BAD_REQUEST` 예외가 발생한다
- [x] 정상 이름은 값이 보존된다

### 1.4 Coupon 생성·계산·발급 가능 (`Coupon`) — v0.3 시간 모델
- [x] `Coupon.create(name, discount, minOrderAmount?, issueStartAt, issueEndAt, useStartAt, useEndAt, now)` 로 템플릿이 생성된다 (deletedAt=null)
- [x] 발급/사용 구간 각각의 종료가 시작 이후가 아니면 `COUPON_BAD_REQUEST` 예외가 발생한다
- [x] `issueEndAt` 이 `now` 이전(과거) 이면 `COUPON_BAD_REQUEST` 예외가 발생한다
- [x] `minOrderAmount` 가 음수면 `COUPON_BAD_REQUEST` 예외가 발생한다 (null 은 허용)
- [x] `calculateDiscount(orderAmount)` 가 `minOrderAmount` 이상이면 `discount.amountFor(orderAmount)` 를 반환한다
- [x] `calculateDiscount(orderAmount)` 가 `minOrderAmount` 미만이면 `COUPON_NOT_APPLICABLE` 예외가 발생한다
- [x] `minOrderAmount` 가 null 이면 어떤 orderAmount 든 하한 검사를 통과한다
- [x] `ensureIssuable(now)` 는 `now` 가 발급 가능 구간(`issueStartAt`~`issueEndAt`, 경계 포함) 밖이면 `COUPON_NOT_APPLICABLE` 예외가 발생한다

### 1.5 Coupon 수정·삭제
- [x] `update(name, discount, minOrderAmount?, issueStartAt, issueEndAt, useStartAt, useEndAt, now)` 로 필드가 갱신된다
- [x] `update` 도 구간 역전·발급 종료 과거·음수 minOrderAmount 를 거부한다
- [x] `softDelete()` 호출 시 `deletedAt` 이 설정되고 `isDeleted()` 가 true 다
- [x] 이미 삭제된 Coupon 의 `softDelete()` 재호출은 멱등이다 (deletedAt 변동 없음)

### 1.6 UserCouponStatus (enum)
- [x] enum 이 `AVAILABLE` · `USED` · `EXPIRED` 세 값을 보유한다 (저장은 `AVAILABLE`/`USED` 만, `EXPIRED` 는 파생 노출용)
- [x] `from("AVAILABLE")` · `from("USED")` 가 대응 enum 으로 매핑된다

### 1.7 UserCoupon 발급·사용·노출상태 (`UserCoupon`) — v0.3 사용 구간 스냅샷
- [x] `UserCoupon.issue(userId, couponId, usableFrom, expiredAt)` 로 발급된다 — 사용 가능 구간을 스냅샷, status=`AVAILABLE`, usedAt=null, issuedAt 설정
- [x] 사용 가능 구간 안에서 `use(at)` 호출 시 status=`USED`, usedAt=at 로 전이된다
- [x] 이미 `USED` 인 쿠폰의 `use(at)` 는 `ALREADY_USED_COUPON` 예외가 발생한다
- [x] 사용 가능 구간(`usableFrom`~`expiredAt`) 밖(시작 전·만료 후) 의 `use(at)` 는 `COUPON_NOT_APPLICABLE` 예외가 발생한다
- [x] `viewStatus(at)` 가 `USED` 면 만료 시각이어도 `USED` 를 반환한다 (사용 우선)
- [x] `viewStatus(at)` 가 `AVAILABLE` + 자기 `expiredAt` 경과면 `EXPIRED` 를 반환한다 (템플릿 무관)
- [x] `viewStatus(at)` 가 `AVAILABLE` + 만료 전이면 `AVAILABLE` 을 반환한다

### 1.8 CouponErrorType
- [x] `COUPON_BAD_REQUEST`(400) · `COUPON_NOT_APPLICABLE`(400) · `COUPON_NOT_FOUND`(404) · `USER_COUPON_NOT_FOUND`(404) · `ALREADY_ISSUED_COUPON`(409) · `ALREADY_USED_COUPON`(409) 정의 (ErrorStatus 매핑 확인)

---

## 2. Phase 2 — Application port (`CouponRepository` · `UserCouponRepository`)

인터페이스만 정의. 테스트 없음 (impl 은 Phase 4 검증).

**CouponRepository**
- `save(coupon): Coupon`
- `findById(id): Coupon?` — soft-deleted 제외 (관리자 상세·수정·삭제·발급)
- `findByIdIncludingDeleted(id): Coupon?` — 삭제 포함 (쿠폰 사용 UC-9)
- `findAll(page, size): PageResult<Coupon>` — soft-deleted 제외, 최신순
- `findAllByIdsIncludingDeleted(ids): List<Coupon>` — 삭제 포함 (내 쿠폰 목록 템플릿 조인)

**UserCouponRepository**
- `save(userCoupon): UserCoupon`
- `findById(id): UserCoupon?`
- `existsByUserIdAndCouponId(userId, couponId): Boolean` — 1인 1매 검사
- `findAllByUserId(userId, page, size): PageResult<UserCoupon>` — 발급 최신순
- `findAllByCouponId(couponId, page, size): PageResult<UserCoupon>` — 발급 내역, 발급 최신순

---

## 3. Phase 3 — Application Facade (`application.coupon.CouponFacade`)

mock 기반 단위 테스트.

#### UC-1. `issueCoupon(userId, couponId)` — 회원 발급
- [x] 유효 템플릿으로 발급하면 `AVAILABLE` UserCoupon 이 사용 가능 구간 스냅샷과 함께 저장되고 `IssuedCouponResult` 가 반환된다
- [x] 템플릿이 없거나 삭제됨 → `COUPON_NOT_FOUND`
- [x] 발급 가능 기간 밖(시작 전·종료 후) → `COUPON_NOT_APPLICABLE`
- [x] 이미 발급받은 템플릿(존재 검사) → `ALREADY_ISSUED_COUPON`

#### UC-2. `getMyCoupons(userId, pageQuery)` — 내 목록
- [x] 보유 발급 쿠폰을 페이지로 반환한다 (발급 최신순 위임 확인)
- [x] 각 항목의 노출 상태가 사용/만료/가능으로 파생된다 (USED·EXPIRED·AVAILABLE 케이스)
- [x] 템플릿이 삭제된 발급 쿠폰도 목록에 노출된다 (`findAllByIdsIncludingDeleted` 위임)

#### UC-3. `getCouponsForAdmin(pageQuery)` — 관리자 목록
- [x] `findAll` 로 위임되고 결과가 `AdminCouponResult` 페이지로 매핑된다

#### UC-4. `getCouponForAdmin(couponId)` — 관리자 상세
- [x] 존재 템플릿 → `AdminCouponResult`
- [x] 미존재/삭제 → `COUPON_NOT_FOUND`

#### UC-5. `registerCoupon(command)` — 관리자 등록
- [x] 유효 입력으로 등록하면 신규 Coupon 이 저장된다
- [x] 발급/사용 구간 위반(역전·발급 종료 과거) / 잘못된 할인 값(도메인 검증 위임) → `COUPON_BAD_REQUEST`

#### UC-6. `updateCoupon(couponId, command)` — 관리자 수정
- [x] 정상 입력으로 name/discount/minOrderAmount/발급·사용 구간이 갱신된다
- [x] 미존재/삭제 → `COUPON_NOT_FOUND`

#### UC-7. `deleteCoupon(couponId)` — 관리자 삭제
- [x] 정상 호출 시 Coupon 이 soft delete 된다 (저장됨)
- [x] 미존재/이미 삭제 → `COUPON_NOT_FOUND`

#### UC-8. `getCouponIssues(couponId, pageQuery)` — 발급 내역
- [x] 템플릿 존재 확인 후 `findAllByCouponId` 위임, `CouponIssueResult` 페이지 매핑
- [x] 미존재/삭제 템플릿 → `COUPON_NOT_FOUND`
- [x] 각 항목 노출 상태가 파생된다

#### UC-9. 쿠폰 사용 (할인 + 소진) — CouponFacade 진입점 제거, OrderFacade 가 직접 조율(Phase 9)
> 초안의 `CouponFacade.applyCoupon(userId, userCouponId, orderAmount)` 진입점은 **채택하지 않는다**(Facade→Facade 금지). 사용 능력은 도메인(`UserCoupon.use`·`Coupon.calculateDiscount`) 이 소유하고, `OrderFacade.placeOrder` 가 쿠폰 port 를 직접 주입받아 같은 트랜잭션에서 호출한다. 아래 시나리오는 Phase 9 통합 테스트가 검증한다.
- [x] 정상: 할인 금액이 주문에 반영되고 UserCoupon 이 `USED` 로 저장된다
- [x] 발급 쿠폰 미존재 또는 소유자 불일치 → `USER_COUPON_NOT_FOUND`
- [x] 이미 사용된 쿠폰 → `ALREADY_USED_COUPON` (`UserCoupon.use`)
- [x] 사용 가능 기간 밖(시작 전·만료) → `COUPON_NOT_APPLICABLE` (`UserCoupon.use`, 발급 쿠폰 스냅샷으로 판정)
- [x] 최소 주문 금액 미달 → `COUPON_NOT_APPLICABLE` (`Coupon.calculateDiscount`)
- [x] 할인액이 정액/정률 종류에 맞게 계산된다 (FIXED·RATE 각각)

---

## 4. Phase 4 — Infrastructure adapter (`CouponRepositoryImpl` · `UserCouponRepositoryImpl`)

Testcontainers 통합 테스트 (`modules:jpa` testFixtures 재사용).

**Coupon**
- [x] save 후 findById 로 동일 도메인 객체가 복원된다 (discount/minOrderAmount/발급·사용 구간 4시각 보존)
- [x] findById 는 soft-deleted Coupon 을 null 로 반환한다
- [x] findByIdIncludingDeleted 는 soft-deleted Coupon 도 반환한다
- [x] findAll 이 createdAt desc + id desc tie-breaker, soft-deleted 제외, 페이징을 적용한다
- [x] findAllByIdsIncludingDeleted 가 삭제 포함 다건을 반환한다

**UserCoupon**
- [x] save 후 findById 로 동일 도메인 객체가 복원된다 (status/usedAt/usableFrom/expiredAt 스냅샷 보존)
- [x] existsByUserIdAndCouponId 가 (회원, 템플릿) 보유를 판정한다
- [x] `(user_id, coupon_id)` UNIQUE 위반 시 저장이 실패한다 (1인 1매 — DataIntegrityViolation)
- [x] findAllByUserId 가 issued_at desc + id desc, 페이징을 적용한다
- [x] findAllByCouponId 가 issued_at desc + id desc, 페이징을 적용한다

---

## 5. Phase 5 — 관리자 채널 인증 (공유 인프라) — ✅ 재사용, 신규 작업 없음

> `/api-admin/**` 전 경로 인증은 `AdminAuthInterceptor`(`WebMvcConfig` 의 `addPathPatterns("/api-admin/**")`) 가 담당한다. `/api-admin/v1/coupons*` 도 자동 포함된다.
- [x] (회귀) `X-Loopers-Ldap` 헤더 누락 → `401` (관리자 목록 엔드포인트 E2E 로 확인)

---

## 6. Phase 6 — 회원 쿠폰 API (행위 — Red→Green, E2E)

> 산출물: `CouponV1Controller`(`/api/v1`), `CouponV1ApiSpec`, `CouponV1Dto`(`IssuedCouponResponse`, `MyCouponsResponse`=PageResult 형태, `MyCouponItem`). `@RequireAuth` + `@LoginUser`.

### 6.1 UC-1 발급 — `POST /api/v1/coupons/{couponId}/issue`
- [x] 정상 발급 → `200 OK` + 발급 쿠폰(status=`AVAILABLE`)
- [x] 미존재/삭제 템플릿 → `404 COUPON_NOT_FOUND`
- [x] 만료 템플릿 → `400 COUPON_NOT_APPLICABLE`
- [x] 이미 발급 → `409 ALREADY_ISSUED_COUPON`
- [x] 미인증 → `401 UNAUTHORIZED`

### 6.2 UC-2 내 목록 — `GET /api/v1/users/me/coupons`
- [x] 인증 회원 조회 → `200 OK` + PageResult(발급 최신순), 각 항목 status 파생(AVAILABLE/USED/EXPIRED)
- [x] `page`/`size` 가 반영되고 페이지 메타가 정확
- [x] 미인증 → `401 UNAUTHORIZED`

---

## 7. Phase 7 — 관리자 쿠폰 API (행위 — Red→Green, E2E)

> 산출물: `CouponV1AdminController`(`/api-admin/v1/coupons`), `CouponV1AdminApiSpec`, DTO(`AdminCouponResponse`, `AdminCouponsResponse`, `RegisterCouponRequest`, `UpdateCouponRequest`, `CouponIssuesResponse`, `CouponIssueItem`). 인증 헤더 `X-Loopers-Ldap`(Phase 5 인터셉터가 경로로 강제).

### 7.1 UC-3 목록 — `GET /api-admin/v1/coupons`
- [x] 인증 관리자 조회 → `200 OK` + PageResult, **최신순**, 삭제 제외
- [x] `page`/`size` 반영, 페이지 메타 정확

### 7.2 UC-4 상세 — `GET /api-admin/v1/coupons/{couponId}`
- [x] 존재 → `200 OK` + `{ id, name, type, value, minOrderAmount, issueStartAt, issueEndAt, useStartAt, useEndAt }`
- [x] 미존재/삭제 → `404 COUPON_NOT_FOUND`

### 7.3 UC-5 등록 — `POST /api-admin/v1/coupons`
- [x] 정상 등록 → `200 OK` + 생성 템플릿
- [x] 잘못된 `type`/`value`/발급 종료 과거 → `400 COUPON_BAD_REQUEST`

### 7.4 UC-6 수정 — `PUT /api-admin/v1/coupons/{couponId}`
- [x] 정상 수정 → `200 OK` + 갱신
- [x] 미존재/삭제 → `404 COUPON_NOT_FOUND`
- [x] 입력 형식 위반 → `400 COUPON_BAD_REQUEST`

### 7.5 UC-7 삭제 — `DELETE /api-admin/v1/coupons/{couponId}`
- [x] 정상 삭제 → `200 OK` + `data: null`, soft delete (E2E: 삭제 후 상세 `404`)
- [x] 미존재/이미 삭제 → `404 COUPON_NOT_FOUND`

### 7.6 UC-8 발급 내역 — `GET /api-admin/v1/coupons/{couponId}/issues`
- [x] 정상 → `200 OK` + PageResult(발급 최신순), 각 항목 `{ userCouponId, userId, status, issuedAt, usedAt }`
- [x] 미존재/삭제 템플릿 → `404 COUPON_NOT_FOUND`

### 7.7 관리자 인증 (공통)
- [x] `X-Loopers-Ldap` 누락 → `401 UNAUTHORIZED` (목록 E2E 회귀)

---

## 8. Phase 8 — api-spec / 문서 동기화 (문서)

- [x] 구현과 api-spec 의 필드명/상태 직렬화/에러 코드 정합 확인
- [x] `EXPIRED` 파생·1인 1매·단일 사용 결정이 구현과 일치함을 부록에 확정
- [x] order 연동(쿠폰 사용 결선·보상) 이 범위 밖임을 명시

---

## 9. Phase 9 — 주문 적용 통합 (행위 — Red→Green) — v0.2 추가

> 쿠폰 사용(UC-9) 을 `OrderFacade.placeOrder` 에 결선한다. 쿠폰은 **주문 1건당 1장**, 적용 실패 시 주문 롤백, 성공 시 즉시 `USED`.

### 9.1 Order 도메인 — 할인 반영
- [x] `Order` 에 `discountAmount`(기본 0) 추가, `couponId` 를 var 로 전환
- [x] `originalAmount`(라인 합계) / `totalAmount`(= originalAmount − discountAmount, 0 하한) 분리
- [x] `applyCoupon(userCouponId, discountAmount)` — 할인 바인딩, 할인은 0..originalAmount 로 보정
- [x] `OrderTest`: 할인 반영 / 0 하한 / 미적용 시 0

### 9.2 OrderFacade — 쿠폰 적용 결선
- [x] 쿠폰 port(`UserCouponRepository`/`CouponRepository`) 직접 주입. `placeOrder` 에서 `userCouponId != null` 이면 발급 쿠폰을 비관 락 조회 → 소유 확인 → `Coupon.calculateDiscount` → `UserCoupon.use` → `order.applyCoupon(...)` 를 같은 `@Transactional` 에서 조율(별도 `CouponFacade.applyCoupon` 진입점 없음 — Facade→Facade 금지)
- [x] `OrderFacadeTest`: 쿠폰 적용 시 할인 반영 + USED 위임 / 미적용 시 쿠폰 호출 없음 / 적용 실패 시 주문·재고 저장 안 함 + 예외 전파 (기존 "couponId 입력 슬롯만" 테스트는 행위 변경으로 교체)

### 9.3 영속 / 교차 도메인 통합
- [x] `OrderEntity` 에 `discount_amount` 컬럼 + 매핑(`from`/`toDomain`)
- [x] `OrderResult` 에 `couponId`·`discountAmount` 노출
- [x] `OrderRepositoryImplIntegrationTest`: 쿠폰 적용 주문 라운드트립(couponId/discountAmount/순 totalAmount 보존)
- [x] `OrderCouponIntegrationTest`(@SpringBootTest): ① 쿠폰 적용 → 할인 반영 + 쿠폰 즉시 USED ② 이미 사용된 쿠폰 → 주문 실패 + 재고 차감·주문 생성 롤백 ③ 타 유저 쿠폰 → `USER_COUPON_NOT_FOUND` + 재고 미차감 + 소유자 쿠폰 AVAILABLE 유지

### 9.4 문서
- [x] requirements v0.2(UC-9 주문 적용 in-scope·1인 1주문·즉시 USED·롤백), api-spec 부록, ERD `orders.discount_amount`/`coupon_id` 동기화

### 9.5 검증 워크플로우 후속 보강 (적대적 리뷰 결과 반영)
- [x] **동시성 단일 사용(HIGH)**: 사용 경로에 발급 쿠폰 행 **비관적 쓰기 락**(`UserCouponRepository.findByIdForUpdate` = `@Lock(PESSIMISTIC_WRITE)`) 도입 — `OrderFacade.placeOrder` 의 쿠폰 블록이 이를 사용해 동시 주문을 직렬화. `OrderCouponIntegrationTest` 에 N-스레드(CountDownLatch) 동시 주문 → 정확히 1건 성공·1회 소진·재고 1회 차감 검증 추가
- [x] **실패 모드 롤백 통합 커버리지**: 만료 / 최소금액 미달 / 미존재(USER_COUPON_NOT_FOUND) / 성공 후 재사용 차단 케이스를 실제 영속 계층 통합 테스트로 추가(재고 원복·쿠폰 상태 확정 검증). 주문 존재 검증은 `OrderJpaRepository.count()` 사용(`getMyOrders` 의 `LocalDateTime.MIN/MAX` 바인딩 함정 회피)
- [x] **정률 오버플로(LOW)**: `Discount.amountFor` 의 RATE 계산을 몫/나머지 분해로 변경해 큰 합계에서 Long 오버플로(조용한 0 클램프) 방지 + 회귀 테스트
- [x] **관리자 응답 할인 노출(MEDIUM)**: `AdminOrderResult` 에 `discountAmount`·`couponId` 추가
- [x] **OrderFacadeTest 보강**: 쿠폰 실패 errorType 3종(@EnumSource) 전파·롤백, 멱등 적중 시 쿠폰 미소진 검증
- [x] **문서 모순 제거**: 락 도입으로 requirements §5/§6 의 '동시성 단일 사용 보장' 이 참이 되어 TBD 의 '락 미도입' 항목을 비관적 락 구현 사실로 갱신

> **사용자 판단 보류(기존 order 도메인 부채, 본 변경과 무관)**: ① order/requirements·plan 의 `PAID` vs 실제 `PAYMENT_PENDING` 구현 모순(결제 단계 구현 의도에 의존) ② ERD `orders.total_price`(스냅샷) vs 코드의 파생 합계 ③ `OrderFacade.getMyOrders` 가 기간 미지정 시 `LocalDateTime.MIN/MAX` 를 MySQL 에 바인딩해 빈 결과를 반환하는 잠재 버그 — 별도 작업으로 분리.

---

## 진행 로그

- 2026-06-09: requirements / api-spec / ERD(coupons·user_coupons) 작성, plan 초안 수립. 발급 1인 1매·사용 최대 1회·만료 파생 결정.
- 2026-06-09: **전 Phase 구현 완료**. Phase 1 도메인(DiscountType·Discount VO·CouponName VO·Coupon·UserCoupon·UserCouponStatus·CouponErrorType, 도메인 테스트 그린) → Phase 2 포트(CouponRepository·UserCouponRepository) → Phase 3 CouponFacade(UC-1~9, mock 단위 테스트) → Phase 4 인프라(CouponEntity·UserCouponEntity·JpaRepository·RepositoryImpl, Testcontainers 통합 테스트 — `@SQLRestriction` 대신 명시적 deleted 필터, UNIQUE(user_id,coupon_id) 1인 1매 검증) → Phase 6 회원 API(CouponV1Controller·발급/내 목록 E2E) → Phase 7 관리자 API(CouponV1AdminController·템플릿 CRUD·발급 내역 E2E + 인증 401 회귀). 상태/할인종류 와이어 값은 enum 이름(대문자) 직렬화. commerce-api 전체 테스트 + ktlintCheck 그린.
- 2026-06-10: **Phase 9 — 주문 적용 통합 완료(v0.2)**. `OrderFacade.placeOrder` 가 같은 트랜잭션에서 `CouponFacade.applyCoupon` 호출 → 주문 1건당 1장, 성공 시 즉시 `USED`, 실패 시 주문·재고·쿠폰 소진 전부 롤백. `Order` 에 `discountAmount`/`applyCoupon` + `originalAmount`/순 `totalAmount`, `OrderEntity.discount_amount` 컬럼, `OrderResult` 노출 추가. OrderTest·OrderFacadeTest 갱신(기존 "입력 슬롯만" 테스트 교체) + `OrderCouponIntegrationTest`(@SpringBootTest) 원자성 검증. requirements v0.2·api-spec·ERD 동기화. 전체 테스트 + ktlintCheck 그린.
- 2026-06-10: **검증 워크플로우(5차원 적대적 리뷰) 후속 보강**. 확인 결함 반영 — 동시 이중 소진(HIGH)을 사용 경로 비관적 락 + N-스레드 동시성 통합 테스트로 차단, 만료/최소금액/미존재/재사용 롤백 통합 테스트 추가, 정률 오버플로(LOW) 안전화, `AdminOrderResult` 할인 노출, OrderFacadeTest errorType 파라미터화·멱등 미소진 검증, requirements §5/§6 ↔ TBD 모순 제거(락 구현 반영). 전체 테스트 + ktlintCheck 그린. 기존 order 도메인 부채(PAID vs PAYMENT_PENDING, total_price 스냅샷, getMyOrders MIN/MAX) 는 별도 분리.
- 2026-06-11: **시간 모델 재설계(v0.3)**. 템플릿 단일 `expiredAt` → 발급 가능 구간(`issueStartAt`/`issueEndAt`)·사용 가능 구간(`useStartAt`/`useEndAt`) 4시각으로 분리. 발급 시 사용 가능 구간을 발급 쿠폰(`usableFrom`/`expiredAt`) 으로 **스냅샷** — 사용 가능 판정·`EXPIRED` 파생이 발급 쿠폰 자신만으로 완결(템플릿 변경·삭제 무관). `ensureIssuable` 은 발급 구간 판정, `UserCoupon.use` 가 단일 사용 + 사용 가능 기간을 함께 캡슐화(Tell-Don't-Ask), `viewStatus(at)` 는 coupon 인자 제거. 엔티티 컬럼(`coupons` 4개·`user_coupons` `usable_from`/`expired_at`)·커맨드·결과·DTO·`OrderFacade` 사용 블록·도메인/통합/E2E 테스트 일괄 반영. requirements v0.3·api-spec·ERD 동기화. 전체 테스트 + ktlintCheck 그린. (이전 초안의 `Discount` VO 표기는 v0.2 `DiscountPolicy` sealed 모델로 이미 대체됨 — §0 결정사항 참고.)
