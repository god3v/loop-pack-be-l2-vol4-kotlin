# 상품 조회 캐시 설계 문서

> 대상: `GET /api/v1/products/{id}` (상세), `GET /api/v1/products` (목록)
> 목표: 자주 읽히고 자주 바뀌지 않는 데이터를 Redis 로 캐시해 DB 부하·응답지연을 줄인다. 캐시는 본질적으로 "정확도 ↔ 속도" 트레이드오프이며, 그 선택을 의식적으로 기록한다.

## 1. 캐시 대상 분석과 함정

| 대상 | 캐시 적합성 | 함정 |
|---|---|---|
| 상품 상세 | 높음 (자주 읽힘, 거의 안 바뀜) | 응답에 `likedByMe`(유저별 값)가 섞여 있어 통째 캐시 불가 |
| 상품 목록 | 중간 | 키 폭발(`brandId × sort × page × size`) + 정밀 무효화 불가 |

- 상세: `ProductDetailResult = product + brand(공유) + likedByMe(유저별)`. 공유 본문만 캐시하고 `likedByMe` 는 매 요청 합성한다.
- 목록: 목록 안에는 여러 상품의 `like_count` 가 들어 있어, 한 상품만 좋아요가 변해도 목록이 stale 해진다. 그러나 "상품 X 가 들어있는 목록 페이지가 무엇인지" 역추적이 불가능하다 → 정밀 무효화 포기, **TTL-only**.

## 2. 구조 결정 — 메커니즘은 infra, 정책은 application

```
application.product.port.ProductCache       (outbound port — 정책 계약)
infrastructure.product.RedisProductCache    (adapter — Redis 메커니즘)
application.product.ProductFacade           (read-through 오케스트레이션)
```

핵심 분리: **캐시 메커니즘(Redis 직렬화·키·TTL)은 infra 어댑터**에, **캐시 정책(무엇을·언제 캐시/무효화할지, read-through)은 application** 에 둔다. 헥사고날 규칙(외부 게이트웨이 = outbound port)과 일치한다.

### 2-1. 왜 `@Cacheable` 대신 RedisTemplate 직접 제어인가

| 이유 | 설명 |
|---|---|
| likedByMe 분리 | `@Cacheable` 은 메서드 반환값 통째 캐시. 유저별 값을 분리하려면 별도 빈 추출 + self-invocation 함정 회피 필요 |
| **Redis 다운 시 폴백** | Spring 기본 `SimpleCacheErrorHandler` 는 캐시 예외를 전파 → Redis 다운 시 요청 실패. DB 폴백하려면 별도 `CacheErrorHandler` 등록 필요. 명시적 `try/catch` 가 "캐시 미스에도 정상 동작"을 코드에 드러낸다 |
| 테스트 | 포트로 두면 Facade 를 fake/mock 캐시로 순수 단위 테스트 가능. `@Cacheable` 은 컨텍스트 + CacheManager 필요 |
| 학습 | 주차 문서가 캐시 흐름 학습을 위해 RedisTemplate 직접 제어를 권장 |

> 균형: 유저 컨텍스트 없는 단순 단건 조회라면 `@Cacheable` 한 줄이 더 깔끔하다. 위 함정들은 모두 우회 가능하므로 절대적 우열이 아니라 **이 맥락의 트레이드오프**다.

### 2-2. 왜 infra Repository 데코레이터 대신 application 경계인가

`CachedProductRepository : ProductRepository` 로 `findById`/`findAll` 을 투명 캐시하는 방식(데코레이터)도 정당한 패턴이다. 그러나 이 도메인에선 application 경계가 더 맞다.

| 관점 | Repository 데코레이터 | application 포트(채택) |
|---|---|---|
| 투명성/재사용 | ✅ 모든 호출자 자동 혜택 | ❌ Facade 가 인지 |
| **staleness 범위** | ❌ `findById` 호출자 전부(신선도 민감 경로 포함) 오염 | ✅ 표시용 읽기 유즈케이스로 한정 |
| 교차-레포 합성(상세=product+brand) | ❌ 단일 레포만 보여 조인 결과 캐시 불가 | ✅ 합성물을 한 단위로 캐시 |
| 유즈케이스 정책(page<N, TTL 차등, member/admin) | ❌ infra 로 정책 누수 | ✅ 유즈케이스에 위치 |

- 가장 결정적인 이유: `findById` 를 Repository 에서 캐시하면 그 메서드의 **모든** 호출자에게 무차별로 staleness 가 적용된다. 유즈케이스(`getProductDetail`)에 캐시를 걸면 staleness 가 "표시용 읽기"에만 갇힌다.
- 단, 상품 목록은 브랜드 조인이 없어(`ProductSummaryResult` 는 `brandId` 만 보유) 데코레이터로도 깔끔하다. **목록만 데코레이터로 떼는 하이브리드**는 향후 유효한 선택지다.

## 3. 키 / TTL / 직렬화 / 무효화

| 항목 | 상세 | 목록 |
|---|---|---|
| 키 | `product:detail:v1:{id}` | `product:list:v1:{brandId\|all}:{sort}:{page}:{size}` |
| 내용 | 공유 본문(product+brand) — `CachedProductDetail` | `PageResult<ProductSummaryResult>` |
| 범위 | 전체 | `page < 5` 만 (핫 경로) |
| TTL | 5분 | 60초 (목록이 더 자주 바뀌니 짧게) |
| 무효화 | TTL + 상품 수정/삭제 시 evict (좋아요는 TTL 감내) | TTL-only (정밀 무효화 불가) |
| 직렬화 | Jackson JSON (String value) | 〃 |

- 키에 버전(`v1`)을 둬 캐시 스키마 변경 시 롤오버 가능.
- `likedByMe` 는 캐시에 넣지 않고 매 요청 `existsByUserIdAndProductId`(인덱스 조회)로 합성한다.

## 4. read-through 흐름

상세:
```
getDetail(id) → 히트면 본문 반환
              → 미스면 DB(product+brand) 조회 → 캐시 put → 본문
그 위에 likedByMe = (userId 있으면 existsBy) 를 합성해 응답
```
목록:
```
page < 5 면: getList(key) → 히트면 반환 / 미스면 DB 조회 → put → 반환
page >= 5 면: 캐시 우회, DB 직행
```

## 5. 장애 / 미스 안전성

어댑터(`RedisProductCache`)가 모든 Redis 호출을 `runCatching` 으로 감싼다.

| 연산 | Redis 정상 | Redis 예외 |
|---|---|---|
| get | 값/대상없음 | **null (= miss)** |
| put / evict | 저장/삭제 | **no-op (무예외)** |

→ Redis 가 죽어도 Facade 는 캐시 미스로 보고 DB 로 정상 동작한다. 캐시는 "있으면 빠르게, 없으면 정확하게"의 보조 계층이다.

## 6. 정합성 트레이드오프 (의식적 수용)

- **TTL 내 staleness**: 좋아요 수가 상세 5분 / 목록 60초 내 옛값일 수 있다. 인기 카운트는 근사 허용.
- **무효화 레이스**: 상세 evict 직후 커밋 전 동시 읽기가 옛 데이터를 재적재 → TTL 까지 stale 잔존. TTL 로 자가 치유.
- **목록 무효화 불가**: 어느 페이지에 상품 X 가 있는지 모르므로 TTL-only. 신규 상품은 목록 TTL 내 노출.
- **적중률 급락 시**: 적중률이 낮으면 캐시가 DB 부하를 못 막고 Redis 왕복 비용만 추가된다 → 핫 경로(page<5, 상세)에 한정하는 이유.

## 7. 로컬 캐시(L1) 보류

| 관점 | 판단 |
|---|---|
| 측정 우선 | Redis(L2)가 병목이라는 증거 없음 |
| 정합성 비용 | 인스턴스마다 L1 분리 → evict 전파에 Redis pub/sub 필요(복잡도↑) |
| 상세 적합성 | 카디널리티 높아 L1 적중률 낮음 |
| 목록 적합성 | 홈 page0 등 소수 핫 키는 후보. 측정 후 도입 |

→ 지금은 Redis-only. 포트 추상화 덕분에 추후 "L1 → L2 → DB" 복합 어댑터로 비파괴 확장 가능.

## 8. 검증 (테스트)

| 테스트 | 검증 내용 |
|---|---|
| `RedisProductCacheTest` (단위) | Redis 예외 시 get=null, put/evict 무예외 — 장애 흡수 |
| `RedisProductCacheIntegrationTest` (Testcontainer) | 상세/목록 put→get 라운드트립, evict, 키 격리(다른 sort/brand=miss) |
| `ProductFacadeTest` (mock) | 히트 시 DB 미조회, 미스 시 적재, page≥5 캐시 스킵, 수정/삭제 시 evict |
| `ProductV1ApiE2ETest` | 캐시 활성 상태로 전 시나리오 통과(테스트 간 Redis flush 로 격리) |

## 9. 관련 코드
- 포트: `application/product/port/ProductCache.kt`
- 어댑터: `infrastructure/product/RedisProductCache.kt`
- 오케스트레이션: `application/product/ProductFacade.kt` (`getProducts`, `getProductDetail`, `updateProduct`/`deleteProduct` evict)
