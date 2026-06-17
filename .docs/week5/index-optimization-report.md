# 상품 목록 조회 인덱스 최적화 리포트

> 대상: `GET /api/v1/products` (상품 목록 조회) — 필터·정렬·페이징
> 목표: 느린 조회의 병목을 EXPLAIN 으로 규명하고, 유즈케이스별 인덱스로 전후 성능을 비교한다.

## 1. 측정 환경

| 항목 | 값 |
|---|---|
| DB | MySQL 8.0.42 (docker `docker-mysql-1`) |
| 데이터 | `products` 30만 건 (alive 293,875 / soft-delete 약 2%) |
| 브랜드 수 | 100 (브랜드당 평균 약 3,000건) |
| 분포 | price 1천~100만, like_count 좌편향(POW(RAND(),3)), created_at 최근 2년 랜덤 |
| 측정법 | `EXPLAIN ANALYZE` actual time, 페이지 크기 20 |
| 시드/쿼리 | `sql/seed.sql`, `sql/baseline.sql`, `sql/after_index.sql` |

## 2. 조회 필터·정렬 유즈케이스 유형

API 가 노출하는 파라미터는 `brandId`(옵션), `sort`(latest/price_asc/likes_desc), `page/size`. 항상 `@SQLRestriction(deleted_at IS NULL)` 이 붙는다.

조합 행렬 (필터 2 × 정렬 3 = 6 유즈케이스):

| # | 필터 | 정렬 | 의미 |
|---|---|---|---|
| U1 | 없음 | created_at DESC | 전체 목록 최신순 (기본/홈) |
| U2 | 없음 | price ASC | 전체 가격 낮은 순 |
| U3 | 없음 | like_count DESC | 인기 상품 |
| U4 | brand_id | created_at DESC | 브랜드 신상 |
| U5 | brand_id | price ASC | 브랜드 가격순 |
| U6 | brand_id | like_count DESC | 브랜드 인기순 (과제 핵심) |

공통: 모든 정렬에 동점 처리용 타이브레이크 `id` 가 붙는다(페이지네이션 안정성). 페이징은 offset 방식.

## 3. 병목 분석 (인덱스 없는 baseline)

인덱스는 `PRIMARY(id)` 뿐. 6 유즈케이스 모두 동일 패턴이었다.

클래식 EXPLAIN (U6, brand=7 + 인기순):
```
type=ALL, key=NULL, possible_keys=NULL, rows=298521, Extra: Using where; Using filesort
```

EXPLAIN ANALYZE 트리 (U1):
```
-> Limit: 20  (actual time=118..118 rows=20)
  -> Sort: created_at DESC, id DESC, limit 20  (actual time=118..118)
    -> Filter: (deleted_at is null)  (rows=293875)
      -> Table scan on products  (rows=300000, actual time=..76.4)
```

| 유즈케이스 | type | 스캔 | 정렬 | actual time |
|---|---|---|---|---|
| U1 최신순 page0 | ALL | 300,000 | filesort 293,875 | 118 ms |
| U1 최신순 offset 100000 | ALL | 300,000 | filesort 100,020 | 180 ms |
| U3 인기순 page0 | ALL | 300,000 | filesort 293,875 | 99 ms |
| U5 brand=7 가격↑ | ALL | 300,000 | filesort(필터후 2,884) | 69 ms |
| U6 brand=7 인기순 | ALL | 300,000 | filesort(필터후 2,884) | 69 ms |
| count brand=7 | ALL | 300,000 | - | 41 ms |

### 병목 4종
1. 풀스캔(`type=ALL`, `key=NULL`): `LIMIT 20` 이어도 30만 행 전부 읽음. 브랜드 필터조차 인덱스가 없어 30만 스캔 후 메모리 필터.
2. filesort(`Using filesort`): 정렬 인덱스가 없어 매 요청 정렬 연산.
3. deep offset: `OFFSET 100000` 은 버릴 10만 행까지 정렬 입력에 포함 → 페이지가 깊을수록 악화.
4. count 동반: Spring Data `Page` 가 목록 + `COUNT(*)` 를 매 요청 동반, 이 count 도 풀스캔.

## 4. 인덱스 설계 — 결정과 근거

설계 공식: **`등치 필터(brand_id) → 정렬 컬럼 → 타이브레이크(id)`** 순. 등치로 한 점에 도달한 뒤 그 안에서 정렬 컬럼이 이미 정렬돼 있으면 seek + 정렬 인덱스 + LIMIT 만 읽으면 된다(풀스캔·filesort 동시 제거).

세 가지 결정을 EXPLAIN 실험으로 검증했다.

### 결정 1 — `deleted_at` 은 인덱스에서 제외 (선택도)
`deleted_at IS NULL` 은 항상 붙지만 98% NULL 이라 선택도가 없다.

| 실험 | 인덱스 | 결과 |
|---|---|---|
| I | (brand_id, like_count, id) | `deleted_at` 은 `Using where` post-filter. LIMIT 20 + 삭제율 2% 라 비용 무시 |
| J | (brand_id, like_count, id, deleted_at) | I 와 플랜 동일, ICP 미적용 — 이득 없이 인덱스만 넓어짐 |
| K | (deleted_at, brand_id, like_count, id) | `ref=const,const` 동작하나 98% NULL near-constant 가 선두라 비대 |

→ 미포함. 단 삭제율이 크게 늘면 재고.

### 결정 2 — 전역/브랜드 인덱스를 각각 둔다 (leftmost prefix)
복합 인덱스는 왼쪽 컬럼부터 정렬된다. 첫 컬럼을 건너뛴 정렬·탐색은 불가.

| 실험 | 인덱스 | 쿼리 | 결과 |
|---|---|---|---|
| A | (brand_id, like_count, id) | 전역 인기순 | `key=NULL, filesort` — 전역 정렬 커버 못함 |
| B | (brand_id, like_count, id) | brand=7 인기순 | `ref, Backward index scan`, filesort 없음 |
| C | (like_count, id) | 전역 인기순 | `index reverse, rows=20`, filesort 없음 |
| D | (like_count, id) only | brand=7 인기순 | filesort 없으나 brand 찾으려 1,936행 walk → 4.33ms |
| E | (brand_id, like_count, id) | brand=7 인기순 | brand seek → 20행, 0.46ms |

→ 전역 정렬엔 전역 인덱스, 브랜드 필터엔 brand 선두 인덱스가 별도로 필요. 전역 인덱스로 브랜드 필터를 대신하면 비용이 데이터 분포에 휘둘림(최악 unbounded). 정렬 3 × 필터 유무 = 6개.

### 결정 3 — 타이브레이크 방향을 주 정렬과 통일 (코드 변경)
인덱스는 한 방향(오름차순)으로 저장되고, DB 는 정/역방향으로 읽을 수 있다. 주 정렬과 타이브레이크의 방향이 다르면(혼합) 한 번의 스캔으로 못 맞춘다.

| 실험 | 인덱스 | 정렬 | filesort? |
|---|---|---|---|
| F | (brand_id, price, id) | price ASC, **id DESC** (기존) | 발생 (혼합 방향) |
| G | (brand_id, price, id) | price ASC, **id ASC** | 없음 |
| H | (brand_id, price ASC, id DESC) | price ASC, id DESC | 없음 (전용 혼합 인덱스) |

→ `toJpaSort` 에서 `price_asc` 의 타이브레이크를 `id ASC` 로 통일(G). 모든 인덱스를 평범한 오름차순으로 만들 수 있고, 내림차순 정렬은 역방향 스캔으로 해결되어 내림차순/혼합 전용 인덱스가 불필요해진다.

### 최종 인덱스 셋
`ProductEntity @Table(indexes=...)` 로 선언. 모두 평범한 오름차순.

```
전역:   (created_at, id)        (price, id)        (like_count, id)
브랜드: (brand_id, created_at, id)  (brand_id, price, id)  (brand_id, like_count, id)
```

| 인덱스 | 커버 유즈케이스 |
|---|---|
| idx_products_created_id | U1 (역방향 스캔) |
| idx_products_price_id | U2 |
| idx_products_like_id | U3 (역방향 스캔) |
| idx_products_brand_created_id | U4 |
| idx_products_brand_price_id | U5 |
| idx_products_brand_like_id | U6 (역방향 스캔) |

## 5. 성능 비교 (before / after)

EXPLAIN ANALYZE 트리 (U1 after):
```
-> Limit: 20  (actual time=0.026..0.065 rows=20)
  -> Filter: (deleted_at is null)
    -> Index scan on products using idx_products_created_id (reverse)  (rows=22)
```

| 유즈케이스 | before | after | 개선 | after 플랜 |
|---|---:|---:|---:|---|
| U1 최신순 page0 | 118 ms | 0.065 ms | ~1800x | idx_created_id reverse, 22행 |
| U1 최신순 offset 100000 | 180 ms | 120 ms | 1.5x | 풀스캔/filesort 제거, **offset 10만행 여전히 walk** |
| U3 인기순 page0 | 99 ms | 0.028 ms | ~3500x | idx_like_id reverse, 22행 |
| U5 brand=7 가격↑ | 69 ms | 0.11 ms | ~600x | idx_brand_price_id, **filesort 사라짐**(타이브레이크 효과) |
| U6 brand=7 인기순 | 69 ms | 0.089 ms | ~770x | idx_brand_like_id reverse, 20행 |
| count brand=7 | 41 ms | 1.9 ms | ~22x | idx_brand_created_id 로 카운트 |

### 결론
- 필터+정렬+얕은 페이지: `type=ALL + Using filesort` → `ref/index + reverse scan`. 풀스캔·filesort 동시 제거, LIMIT 만큼(약 20행)만 읽어 1000배 내외 개선.
- 타이브레이크 통일로 U5(가격순)의 filesort 가 평범한 인덱스만으로 제거됨을 실측 확인.
- **deep offset(120ms)은 인덱스로 해결되지 않는다**: 순서는 잡혔으나 `OFFSET 100000` 이 버릴 10만 행을 인덱스에서 그대로 walk. → 커서(keyset) 페이징으로 별도 해결 필요(Phase 3).

## 6. 남은 트레이드오프
- `like_count` 인덱스 2개는 좋아요 등록/취소마다 갱신되는 쓰기 비용을 낸다(핫 컬럼). 인기 목록 캐시(Phase 5)로 읽기 부하를 보완해 비용 대비 효과를 맞춘다.
- 인덱스 6개의 쓰기·저장 비용은 트래픽 가정에 따라 재단할 수 있다. 현재는 모든 노출 조합을 커버하는 풀세트로 두고, 측정 근거가 쌓이면 사용 빈도가 낮은 인덱스를 제거한다.
