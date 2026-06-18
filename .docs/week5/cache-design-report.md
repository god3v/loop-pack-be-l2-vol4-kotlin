# 상품 조회 캐시 설계 리포트

> 대상: `GET /api/v1/products/{id}` (상세), `GET /api/v1/products` (목록)
> 목표: 읽기 병목을 Redis 캐시로 완화하고, 키·TTL·무효화·장애 안전성의 구조 결정과 트레이드오프를 근거와 함께 기록한다.
> 인덱스 리포트(`index-optimization-report.md`)의 후속 — 인덱스로 단건 지연을 낮춘 뒤, 반복 요청의 부하를 캐시로 막는 단계다.

## 1. 병목 지점 분석 — 왜 캐시인가

인덱스 적용 후 단건 조회 지연은 이미 낮다(목록 page0 0.065ms, 브랜드+인기순 0.089ms). 그럼에도 캐시가 필요한 이유는 **단건 지연이 아니라 반복·집중 부하**다.

| 병목 | 인덱스로 해결됨? | 캐시의 역할 |
|---|---|---|
| 동일 핫키 반복 조회 | ❌ (매번 DB 재실행) | TTL 창당 DB 1회로 수렴 — DB 부하·커넥션 절감 |
| 목록의 `COUNT(*)` 동반 | 부분 (여전히 수 ms) | 캐시 히트 시 count 자체를 생략 |
| 비인증 기본 진입 집중 | ❌ | `all:latest:0:20` 한 키에 read 극집중 → 캐시가 가장 큰 효과 |

- 캐시를 이용하게 되면 필연적인 문제점이 있는데 바로 **데이터 정합성** 문제이다.
- 이전에는 DB에서 조회와 작성을 처리하였기 때문에 정합성 문제가 없지만 캐시라는 또 다른 저장소를 이용하기 때문에 실시간성이 떨어진다.
- 따라서 적절한 캐시 읽기 전략(Read Cache Strategy)과 캐시 쓰기 전략(Write Cache Strategy)를 통해 캐시와 DB간의 데이터 불일치 문제를 극복하면서도 빠른 성능을 잃지 않도록 설계한다.
- 읽기는 쓰기의 수~수십 배이고, 그중에서도 **비인증 기본 목록(`brandId` 없음 + 최신순 + 0페이지)** 한 곳에 트래픽이 쏠린다. 이 핫키를 캐시로 막는 것이 핵심 이득이다.
- 단, 캐시는 **있으면 빠르게, 없으면 정확하게**의 보조 계층이다. 캐시가 못 막는 경로(deep offset, 비캐시 페이지)는 인덱스·페이징 설계로 따로 다룬다.

### 두 대상의 함정
- **상세**: 응답에 `likedByMe`(로그인 유저별 값)가 섞여 있어 통째 캐시 불가 → 공유 본문만 캐시하고 `likedByMe`는 매 요청 합성.
- **목록**: 키가 `brandId × sort × page × size` 로 폭발하고, 목록 안에 여러 상품의 `like_count`가 들어 있어 **정밀 무효화가 불가능**(상품 X가 어느 목록 페이지에 있는지 역추적 불가) → TTL-only.

### 측정: 캐시 HIT vs MISS (실측)
데이터 계층 직접 측정. MISS=DB 경로(`EXPLAIN ANALYZE`, warm), HIT=Redis GET(`redis-benchmark`, 1.6KB 값). 30만 건.

| 경로 | MISS (DB) | HIT (Redis GET) | 개선 |
|---|---:|---:|---:|
| 목록 기본 핫키(필터X, page0) | **~49 ms** (find 0.024 + count 49) | **~0.10 ms** | ~490x |
| 목록 브랜드 핫키(brand=7) | ~2.9 ms (find 0.09 + count 2.8) | ~0.10 ms | ~29x |
| 상세(product PK + brand PK) | ~0.1 ms | ~0.10 ms | ~1x |

- 목록 MISS 비용은 **`COUNT(*)`가 지배**: 기본 핫키는 `deleted_at IS NULL` 전수 카운트(294k행) ~49ms, `find LIMIT 20`은 0.024ms. 캐시 HIT 는 count 자체를 생략한다. 트래픽이 가장 쏠리는 기본 핫키에서 효과 최대.
- 상세는 PK 점조회라 DB 도 ~0.1ms → **지연 이득은 ~1x**. 상세 캐시의 가치는 지연이 아니라 **DB 오프로드**(핫 상품 반복 조회의 커넥션·부하 절감).
- 처리량: Redis GET **275,103 rps**(c=50, p50 0.095ms) vs DB count(*) ~49ms/건(코어당 ~20건/s). 동시성이 오를수록 비선형으로 벌어진다.

> 주의: 데이터 계층 수치(DB 쿼리 vs Redis GET)다. 앱 직렬화/역직렬화·네트워크·Spring 오버헤드 제외 — 절대값보다 배수·경향으로 해석한다.

## 2. 구조 결정

### 2-1. 메커니즘은 infra, 정책은 application
```
application.product.port.ProductCache       (outbound port — 정책 계약)
infrastructure.product.RedisProductCache    (adapter — Redis 메커니즘)
application.product.ProductFacade           (cache-aside 오케스트레이션)
```
- 캐시 **메커니즘**(Redis 직렬화·키·TTL)은 infra 어댑터, 캐시 **정책**(무엇을·언제 캐시/무효화, read-through)은 application 에 둔다. 헥사고날 규칙(외부 게이트웨이 = outbound port)과 일치한다.
- 이 포트 추상화 덕분에 추후 로컬 캐시(L1)를 "L1 → L2(Redis) → DB" 복합 어댑터로 끼워도 Facade 는 바뀌지 않는다.

### 2-2. 왜 `@Cacheable` 대신 RedisTemplate 직접 제어
| 이유 | 설명 |
|---|---|
| likedByMe 분리 | `@Cacheable`은 메서드 반환값 통째 캐시. 유저별 값 분리하려면 별도 빈 추출 + self-invocation(같은 빈 내부 호출은 프록시 우회) 함정 회피 필요 |
| **Redis 다운 시 폴백** | Spring 기본 `SimpleCacheErrorHandler`는 캐시 예외를 전파 → Redis 다운 시 요청 실패. DB 폴백하려면 별도 `CacheErrorHandler` 등록 필요. 명시적 `try/catch`가 "캐시 미스에도 정상 동작"을 코드에 드러낸다 |
| 테스트 | 포트로 두면 Facade 를 fake/mock 으로 순수 단위 테스트 가능 |
| 학습 | 주차 문서가 캐시 흐름 학습을 위해 RedisTemplate 직접 제어를 권장 |

> 균형: 유저 컨텍스트 없는 단순 단건이면 `@Cacheable` 한 줄이 더 깔끔하다. 절대 우열이 아니라 이 맥락(likedByMe·DB폴백·헥사고날)의 트레이드오프다.

### 2-3. 왜 infra Repository 데코레이터 대신 application 경계
`CachedProductRepository : ProductRepository` 로 `findById`/`findAll` 을 투명 캐시하는 방식도 정당하나, 이 도메인엔 application 경계가 맞다.

| 관점 | Repository 데코레이터 | application 포트(채택) |
|---|---|---|
| 투명성/재사용 | ✅ | ❌ Facade 가 인지 |
| **staleness 범위** | ❌ `findById` 호출자 전부(정합성 민감 경로 포함) 오염 | ✅ 표시용 읽기 유즈케이스로 한정 |
| 교차-레포 합성(상세=product+brand) | ❌ 단일 레포만 보여 조인 결과 캐시 불가 | ✅ 합성물을 한 단위로 캐시 |
| 유즈케이스 정책(page<N, TTL 차등, member/admin) | ❌ infra 로 정책 누수 | ✅ 유즈케이스에 위치 |

- 가장 결정적: `findById` 를 Repository 에서 캐시하면 그 메서드의 **모든** 호출자가 staleness 를 먹는다. 유즈케이스에 걸면 "표시용 읽기"에만 갇힌다.
- 단 목록은 브랜드 조인이 없어(`ProductSummaryResult`는 `brandId`만) 데코레이터로도 깔끔하다. 목록만 데코레이터로 떼는 하이브리드는 향후 선택지.

### 2-4. 왜 Cache-Aside(읽기) + Write-Around·evict(쓰기) 패턴인가
우리가 채택한 패턴을 정확히 적으면:
- 읽기 = **Cache-Aside(lazy loading)**: Facade 가 캐시를 직접 조회 → 미스면 DB 조회 → 캐시 적재. (캐시가 로더를 갖고 미스 시 내부에서 DB 를 읽어주는 strict **Read-Through** 와 구분된다 — 우리는 RedisTemplate 직접 제어라 cache-aside.)
- 쓰기 = **Write-Around + evict**: 상품 수정/삭제는 DB 에만 쓰고 캐시는 새 값으로 갱신하지 않고 **무효화(evict)** 한다. 다음 읽기가 lazy 하게 재적재.

| 패턴 | 동작 | 채택? / 이유 |
|---|---|---|
| **Cache-Aside** (읽기 채택) | 앱이 get→miss→DB→put | ✅ 미스 경로가 비단순(상세=product+brand 합성, 목록=findAll)이라 앱이 제어해야 깔끔. 캐시 다운=그냥 miss→DB 폴백 |
| Read-Through | 캐시 라이브러리가 미스 시 내부 로더로 DB 적재 | ❌ 합성 로직을 캐시 안에 숨겨 계층이 흐려짐. RedisTemplate 직접 제어와 불일치 |
| Write-Through | 쓰기 시 캐시도 새 값으로 갱신 | ❌ "delete-don't-update": 갱신은 동시 쓰기 순서 꼬임 + 안 읽힐 값까지 적재. evict 가 멱등·단순 |
| Write-Behind | 캐시 먼저, DB 비동기 반영 | ❌ 진실원은 즉시 DB 일관해야(주문·재고). 유실 위험, 읽기 최적화 목적과 무관 |
| **Write-Around** (쓰기 채택) | DB 만 쓰고 캐시는 미적재(+evict) | ✅ 쓰기 드물고(admin) 읽기 지배 → 쓰기로 캐시 오염 안 하고 읽기가 핫셋만 lazy 적재 |
| Refresh-Ahead | 만료 전 선제 갱신 | ⏳ 알려진 핫키용. 핫키 stampede 대책으로 차주 후보 |

> 한 줄 요약: **읽기 지배 + 쓰기 드묾 + 미스 경로가 합성 로직 + 캐시는 보조(다운 시 DB 폴백)** → Cache-Aside 읽기 + Write-Around·evict 쓰기가 가장 단순·안전한 조합이다.

## 3. 캐시 전략 (키 / 내용 / 범위 / TTL / 무효화)

| 항목 | 상세 | 목록 |
|---|---|---|
| 키 | `product:detail:v1:{id}` | `product:list:v1:{brandId\|all}:{sort}:{page}:{size}` |
| 내용 | 공유 본문(product+brand) — `CachedProductDetail` | `PageResult<ProductSummaryResult>` |
| 범위 | 전체 | `page < 5` 만 (핫 경로) |
| TTL | 5분 | 60초 |
| 무효화 | TTL + 상품 수정/삭제 시 evict (좋아요는 TTL 감내) | TTL-only |
| 직렬화 | Jackson JSON(String value) | 〃 |

### 키 설계
- `v1` 버전 prefix — 캐시 스키마 변경 시 키 전체 롤오버용.
- `brandId` 없으면 `all` 로 고정해 키를 정규화. 정렬은 `sort.key`(latest/price_asc/likes_desc).
- `size`는 키 구성요소이자 클라이언트 제어값이라 상한 캡(100)을 둬 키스페이스 폭발을 막는다(§5).

### TTL 근거
- 상세 5분: 자주 안 바뀌는 정보(이름·가격·브랜드). 길게 잡아 적중률↑.
- 목록 60초: 신규 상품 노출·`like_count` 변동을 더 자주 반영해야 해 짧게.

### 무효화 전략
- 상세는 키가 명확(`{id}`)해 **상품 수정/삭제 시 즉시 evict** + TTL 안전망. 가격/삭제는 즉시 반영돼야 하므로.
- 좋아요는 evict하지 않는다 — 고빈도라 evict하면 캐시 churn↑, 카운트는 근사 허용.
- 목록은 **정밀 무효화 불가** → TTL-only. (열거 없이 무효화하려면 버전 네임스페이스(version-bump)가 표준 해법이나, 상품 쓰기마다 전 목록 캐시를 식히는 비용이 있어 현재는 미적용 — §6 (A))

### 조회 흐름 (cache-aside)
```
상세: getDetail(id) → 히트면 본문 / 미스면 DB(product+brand) → put → 본문
      그 위에 likedByMe = (로그인 시 existsBy) 합성해 응답
목록: page<5 → getList(key) → 히트면 반환 / 미스면 DB → put → 반환
      page>=5 → 캐시 우회, DB 직행
```

## 4. 장애 / 미스 안전성

어댑터(`RedisProductCache`)가 모든 Redis 호출을 `read`/`write`/`evict` 헬퍼로 감싸 저장소 장애를 흡수한다.

| 연산 | Redis 정상 | Redis 예외 |
|---|---|---|
| get | 값/대상없음 | **null (= miss)** |
| put / evict | 저장/삭제 | **no-op (무예외)** |

→ Redis 가 죽어도 Facade 는 캐시 미스로 보고 DB 로 정상 동작한다.

### read=replica / write=master 분리
- 읽기는 `@Primary`(REPLICA_PREFERRED) 템플릿, 쓰기/삭제는 master 템플릿으로 분리. 핫키 read 를 replica 로 분산해 **master 단일 read 천장**을 완화한다.
- 트레이드오프: put/evict 직후 replica read 는 복제 지연(ms)만큼 옛값/없음을 볼 수 있다. put 후 miss 는 DB 폴백, evict 후 stale 은 ms 단위라 우리 TTL(분) 수용 범위 내.

## 5. 견고화 (트래픽 10배 점검 결과)

| 항목 | 상태 | 내용 |
|---|---|---|
| `size` 상한 캡(100) | ✅ 적용 | 대형 스캔 + 캐시 키스페이스 폭발 + 미스 증폭 동시 방어 |
| read replica 전환 | ✅ 적용 | master 단일 read 천장 완화 (§4) |
| 핫키 stampede | ⏳ 차주 | 단일 핫키 만료 tick 에 동시 미스가 같은 `count(*)`를 폭격. single-flight / stale-while-revalidate / refresh-ahead 중 택1 (single-flight = 미스 시 하나만 재계산하고 나머지는 대기) |
| 로컬 캐시(L1) | ⏳ 보류 | 다중 인스턴스 정합성 비용(evict 전파에 pub/sub 필요) + 상세 카디널리티 높아 적중률 낮음. 핫 목록 키 한정으로 측정 후 |

## 6. 정합성 트레이드오프 (의식적 수용)

| # | 시나리오 | 심각도 | 현재 처리 |
|---|---|---|---|
| A | soft-delete/가격변경 상품이 목록 캐시에 최대 60초 잔존 → 클릭 시 상세 NOT_FOUND, 표시가≠결제가 | high | **수용**(차주 검토). 근본 해결은 목록 version-bump(상품 CRUD 시 버전 올려 옛 목록 무효화) |
| B | 좋아요로 카운트 변동 시 목록(60s)/상세(5분) 카운트 비대칭 | medium | **수용**. like_count 는 근사값. evict-on-like 는 churn 유발이라 안 함 |
| — | evict-before-commit 레이스: 트랜잭션 커밋 전 evict → 동시 읽기가 옛값 재적재 | — | TTL 자가치유. 개선: 커밋 후 evict(afterCommit) |
| — | `like_count` 비정규화 드리프트(장기 누적) | — | 재계산 배치로 사후 교정(차주, `commerce-batch`) |

- TTL 내 staleness 와 무효화 레이스는 캐시의 본질적 비용이며, **모든 stale 은 결국 TTL 이 정정**한다는 게 마지막 보루다.
- 목록은 정밀 무효화가 불가능해 TTL-only — 위 (A)를 즉시성 있게 막으려면 version-bump 가 필요하나, 상품 쓰기 빈도 대비 비용을 따져 차주 결정한다.

## 7. 검증 (테스트)

| 테스트 | 검증 내용 |
|---|---|
| `RedisProductCacheTest` (단위) | Redis 예외 시 get=null, put/evict 무예외 — 장애 흡수 |
| `RedisProductCacheIntegrationTest` (Testcontainer) | 상세/목록 put→get 라운드트립, evict, 키 격리(다른 sort/brand=miss) |
| `ProductFacadeTest` (mock) | 히트 시 DB 미조회, 미스 시 적재, page≥5 캐시 스킵, 수정/삭제 시 evict |
| `ProductV1ApiE2ETest` | 캐시 활성 상태로 전 시나리오 통과(테스트 간 Redis flush) + size 캡(100) |

## 8. 관련 코드 / 백로그
- 코드: `application/product/port/ProductCache.kt`, `infrastructure/product/RedisProductCache.kt`, `application/product/ProductFacade.kt`, `interfaces/api/product/ProductV1Controller.kt`(size 캡)
- 차주 백로그(`plan.md` 후속 섹션): 핫키 stampede, 목록 version-bump(시나리오 A), like_count 재계산 배치, deep offset 커서 페이징
