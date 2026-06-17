# Round5 읽기 성능 — before/after 측정 기록

- 환경: MySQL 8.0.42 (docker `docker-mysql-1`), `products` 30만 건(293,875 alive, ~2% soft-delete), 100 브랜드
- 측정: `EXPLAIN ANALYZE` 의 actual time (쿼리 실제 실행). 워밍업 후 대표값. 페이지 크기 20.
- 인덱스 상태(baseline): `PRIMARY(id)` **단 하나** — 모든 조회가 풀스캔.

## Phase 1 — Baseline (인덱스 없음)

| # | 시나리오 | 접근(type) | 스캔 행 | 정렬 | actual time |
|---|----------|-----------|---------|------|-------------|
| 1 | 최신순 page0 | ALL(풀스캔) | 300,000 | filesort 293,875 | **118 ms** |
| 2 | 최신순 offset 100000 | ALL | 300,000 | filesort 100,020 | **180 ms** |
| 3 | 인기순 page0 | ALL | 300,000 | filesort 293,875 | **99 ms** |
| 4 | brand=7 + 가격↑ | ALL | 300,000 | filesort (필터후 2,884) | **69 ms** |
| 5 | brand=7 + 인기순 | ALL | 300,000 | filesort (필터후 2,884) | **69 ms** |
| 6a | count 전체 | ALL | 300,000 | - | **47 ms** |
| 6b | count brand=7 | ALL | 300,000 | - | **41 ms** |

### 공통 병목 (전 쿼리 동일 패턴)
- `Table scan on products` — LIMIT 20 을 받아도 **항상 30만 행 전부 읽음**. `key=NULL`.
- `Using filesort` — 정렬 인덱스가 없어 매번 정렬 연산. (4)(5) 는 brand 로 2,884건만 필요한데도 30만 풀스캔 후 정렬.
- `LIMIT/Offset 20/100000` — deep offset 은 버릴 10만 행까지 정렬 입력에 포함 → 페이지 깊어질수록 비용 증가.
- count 쿼리도 풀스캔. Spring Data `Page` 는 목록 + count 를 매 요청 동반.

> 클래식 EXPLAIN (5): `type=ALL, key=NULL, rows=298521, Extra: Using where; Using filesort`

### 해석
- 300만~1000만으로 늘거나 동시 요청이 쌓이면 위 ms 는 선형 이상으로 악화(버퍼풀 압박·정렬 메모리·CPU). 단건 69~180ms 도 목록 API 로는 이미 느림.
- 가장 손댈 가치가 큰 지점: **filter(brand_id) + sort(컬럼) 을 인덱스로 커버**해 (a) 풀스캔 제거 (b) filesort 제거.

## Phase 2 — 인덱스 설계 실험 (토론 근거)

페이지 20, brand_id=7(≈2,884 alive). 인덱스 1개씩 만들며 EXPLAIN.

### Decision 2 — leftmost prefix / 인덱스 개수
| 실험 | 인덱스 | 쿼리 | 결과 |
|---|---|---|---|
| A | (brand_id, like_count, id) | 전역 인기순 | `key=NULL, filesort` — **전역 정렬 커버 못함** |
| B | (brand_id, like_count, id) | brand=7 인기순 | `ref, Backward index scan`, rows=2938, filesort 없음 |
| C | (like_count, id) | 전역 인기순 | `index reverse, rows=20`, filesort 없음 |
| D | (like_count, id) only | brand=7 인기순 | filesort 없음이나 brand 찾으려 **1,936행 훑음 → 4.33ms** |
| E | (brand_id, like_count, id) | brand=7 인기순 | brand seek → **20행, 0.46ms** (≈9배) |

→ 전역 정렬엔 전역 인덱스, 브랜드 필터엔 brand 선두 인덱스가 별도로 필요. 전역 인덱스로 브랜드 필터를 대신하면 비용이 분포에 휘둘림(최악 unbounded).

### Decision 3 — ASC/DESC + id 타이브레이크
| 실험 | 인덱스 | 정렬 | filesort? |
|---|---|---|---|
| F | (brand_id, price, id) all-ASC | price ASC, **id DESC** (앱 현재) | **filesort 발생** (혼합 방향) |
| G | (brand_id, price, id) all-ASC | price ASC, **id ASC** | filesort 없음 |
| H | (brand_id, price ASC, **id DESC**) | price ASC, id DESC | filesort 없음 (전용 혼합 인덱스) |

→ price_asc 의 `id DESC` 타이브레이크가 혼합 방향을 만들어 filesort 유발. 타이브레이크를 주 정렬 방향과 통일(price면 id ASC)하면 평범한 오름차순 인덱스로 해결.

### Decision 1 — deleted_at 포함 여부
| 실험 | 인덱스 | 결과 |
|---|---|---|
| I | (brand_id, like_count, id) | deleted_at 은 `Using where` post-filter, 2% 삭제율이라 비용 무시 |
| J | (brand_id, like_count, id, deleted_at) | (I)과 플랜 동일, ICP 안 붙음 — 이득 없이 인덱스만 넓어짐 |
| K | (deleted_at, brand_id, like_count, id) | `ref=const,const` 동작하나 98% NULL near-constant 선두라 비대 |

→ 현재 삭제율(2%)에선 deleted_at 미포함. 삭제율이 커지면 재고.

## Phase 2 — 인덱스 적용 후 (확정 셋)

확정 인덱스(= `ProductEntity @Table(indexes)`, 평범한 오름차순 6개):
```
전역:   (created_at,id)  (price,id)  (like_count,id)
브랜드: (brand_id,created_at,id)  (brand_id,price,id)  (brand_id,like_count,id)
```
타이브레이크 방향 통일(`toJpaSort`): price_asc → id ASC. 모든 정렬을 정/역방향 스캔으로 커버, filesort 0.

| # | 시나리오 | before | after | 개선 | 사용 인덱스 / 비고 |
|---|----------|-------:|------:|-----:|---|
| 1 | 최신순 page0 | 118 ms | **0.065 ms** | ~1800x | idx_created_id (reverse), 22행만 |
| 2 | 최신순 offset 100000 | 180 ms | **120 ms** | 1.5x | 풀스캔/filesort 제거됐으나 **offset 10만행 여전히 walk** |
| 3 | 인기순 page0 | 99 ms | **0.028 ms** | ~3500x | idx_like_id (reverse), 22행만 |
| 4 | brand=7 + 가격↑ | 69 ms | **0.11 ms** | ~600x | idx_brand_price_id, **filesort 사라짐**(타이브레이크 수정 효과) |
| 5 | brand=7 + 인기순 | 69 ms | **0.089 ms** | ~770x | idx_brand_like_id (reverse), 20행 |
| 6b | count brand=7 | 41 ms | **1.9 ms** | ~22x | idx_brand_created_id 로 카운트 |

### 해석
- 필터+정렬+얕은 페이지: `type=ALL+filesort` → `ref/index + reverse scan` 으로 **풀스캔·filesort 동시 제거**, LIMIT 만큼(20여 행)만 읽음. 1000배 내외.
- (4) `price ASC, id ASC` 로 통일하니 평범한 `(brand_id, price, id)` 인덱스로 filesort 제거 확인.
- **(2) deep offset 만 여전히 느림(120ms)**: 인덱스로 순서는 잡혔지만 `LIMIT 20 OFFSET 100000` 은 버릴 10만 행을 인덱스에서 그대로 walk. → **인덱스로 안 풀리는 문제 = Phase 3 커서(keyset) 페이징 대상.**

## Phase 3 — 정렬/필터 최적화 (커서 페이징)
> (작성 예정)
