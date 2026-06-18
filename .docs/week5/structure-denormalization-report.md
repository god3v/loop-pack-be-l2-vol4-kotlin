# 좋아요 수 정렬 — 정규화 vs 비정규화 구조 비교 리포트

> 대상: "좋아요 순(인기순) 정렬" (`GET /api/v1/products?sort=likes_desc`)
> 목표: `like_count` 비정규화의 선택 근거를 **정규화 대비 before/after 수치**로 입증한다.

## 1. 배경 — 왜 "역실험"인가

본 프로젝트는 처음부터 `products.like_count` 컬럼을 **비정규화**해 두었다. 그래서 과제 ②("비정규화로 좋아요 순 정렬 성능 개선")의 *구조*는 이미 충족돼 있었으나, **정규화(join + group by) 대비 얼마나 빠른지 before/after 수치가 없었다.**

이를 메우기 위해 **역실험**을 수행했다 — `likes` 원장을 현실 규모로 채우고, 같은 "좋아요 순 정렬"을 세 구조로 측정해 비교한다. (코드는 비정규화 그대로 두고, 정규화 쿼리는 측정용으로만 실행)

## 2. 실험 환경

| 항목 | 값 |
|---|---|
| `products` | 30만 건 (alive 293,875) |
| `likes` | **약 200만 건** (`sql/seed_likes.sql`), 289,732개 상품에 분포, 한 상품 최대 12,491 likes |
| `likes` 분포 | user 30만 분산 + product 좌편향(`POW(RAND(),2.5)`) → 인기 집중 |
| 측정 | `EXPLAIN ANALYZE` actual time, LIMIT 20 |

> 주의: `products.like_count`(독립 시드)와 `likes` 행 수는 값이 일치하지 않는다. 본 실험은 "값 일치"가 아니라 **쿼리 구조의 비용**을 비교한다.

## 3. 측정 결과 — 3-way

같은 "좋아요 순 정렬 LIMIT 20"을 세 구조로:

| # | 구조 | 전역 인기순 | 브랜드(7) 인기순 |
|---|---|---:|---:|
| AS-IS① | 정규화 (join+group by, `likes` 인덱스 없음) | **2,306 ms** | 493 ms |
| AS-IS② | 정규화 + `likes(product_id)` 인덱스 | **797 ms** | 20.4 ms |
| TO-BE | 비정규화 (`like_count` 컬럼 + 복합 인덱스) | **0.047 ms** | 0.219 ms |

**비정규화 개선폭**: 전역 ~17,000x(vs ②) / ~49,000x(vs ①), 브랜드 ~93x(vs ②) / ~2,250x(vs ①).

### EXPLAIN 근거 (전역)
AS-IS① — likes 전수를 hash join 후 임시테이블 집계 + 정렬:
```
-> Sort: cnt DESC  (actual time=2306..2306)
  -> Aggregate using temporary table  (actual time=2256, rows=293874 groups)
    -> Left hash join (l.product_id = p.id)  (actual rows=1.97e6)
        -> Table scan on p (300000)
        -> Hash → Covering index scan on l (2e6 rows 전부 읽어 해시 구성)
```
AS-IS② — 인덱스로 join 은 빨라졌으나, 여전히 293,875개 상품 전부 순회 집계 + 정렬:
```
-> Sort: cnt DESC  (actual time=797..797)
  -> Group aggregate: count(l.id)  (actual time=..751, rows=293875)
    -> Nested loop left join  (actual rows=1.97e6)
        -> Index scan on p using PRIMARY (300000)
        -> Covering index lookup on l using idx_likes_product (loops=293875)
```
TO-BE — 정렬 인덱스를 역방향으로 20개만 읽고 종료:
```
-> Limit: 20  (actual time=0.0166..0.0471)
  -> Index scan on products using idx_products_like_id (reverse)  (rows=22)
```

## 4. 해석 — 왜 정규화는 인덱스로도 못 구하나

1. **정규화는 `LIMIT 20`을 살리지 못한다.** 상위 20개를 알려면 *모든 상품의 좋아요를 세어(GROUP BY)* 정렬해야 한다. 전역은 293,875개 그룹 전부를 집계·정렬한 뒤에야 20개를 자른다.
2. **`likes(product_id)` 인덱스를 줘도(②) 전역 797ms.** 인덱스는 "상품당 좋아요 조회"를 빠르게 할 뿐, **상품 전수 순회 + 집계 + 정렬**이라는 본질을 못 없앤다(join 만 hash→nested loop 로 개선될 뿐).
3. **비정규화는 `LIMIT 20`을 산다.** `like_count`가 이미 정렬돼 있어 인덱스를 역방향으로 20개만 읽고 끝난다 — join·group by·임시테이블이 통째로 사라진다.
4. 브랜드 필터는 집계 대상이 2,884개로 줄어 정규화②도 20ms 로 개선되지만, 여전히 비정규화(0.2ms)의 ~90배다.

> 핵심: "좋아요 순 정렬"은 **집계+정렬을 LIMIT 전에 끝내야 하는** 형태라, 정규화로는 인덱스를 줘도 전수 집계를 피할 수 없다. 비정규화는 그 집계 결과를 컬럼에 미리 굳혀(pre-aggregate) LIMIT 를 인덱스로 살린다.

## 5. 비정규화의 댓가 (trade-off)

빠른 읽기는 공짜가 아니다.

| 비용 | 내용 | 현재 대응 |
|---|---|---|
| 쓰기 비용 | 좋아요마다 `like_count` UPDATE + 이를 포함한 인덱스 2종 갱신 | 좋아요는 캐시 evict 안 함(§캐시), 인덱스 6종 중 2종만 like_count 포함 |
| 동시성 | 동시 좋아요/취소 시 카운트 정확성 | 원자 증감(`+1/-1`) + 단일 DELETE 게이트, 동시성 4종 테스트 |
| 정합성 드리프트 | 장기적으로 `like_count` ≠ `COUNT(likes)` 가능 | 재계산 배치로 사후 교정(차주) |

→ 읽기 ~17,000x 개선이 위 쓰기 비용을 압도한다. "읽기 지배 + 인기순이 핵심 정렬"인 커머스에선 비정규화가 정답.

## 6. 결론

- **좋아요 순 정렬은 정규화로 인덱스를 줘도 못 구한다**(전역 797ms) — 전수 집계+정렬이 본질이기 때문.
- **비정규화 `like_count` + 정렬 인덱스**가 LIMIT 를 살려 0.05ms 로 끝낸다(~17,000x).
- 대가(쓰기·드리프트)는 원자 증감·동시성 테스트·(차주)재계산 배치로 관리 가능한 수준.

### 대안 — Materialized View / Pre-aggregation (Nice-to-Have)
`like_count` 컬럼 대신 **조회 전용 집계 테이블/뷰**(예: `product_like_view(product_id, like_count)`)를 배치/이벤트로 적재하는 방식도 동일한 "미리 집계" 전략이다. 실시간성을 더 내주는 대신 원본 `products` 쓰기 경로를 건드리지 않는다. 본 프로젝트는 단순성을 위해 컬럼 비정규화를 택했고, 규모가 커지면 조회 전용 구조로 분리할 수 있다.

## 7. 재현
```
docker exec -i docker-mysql-1 mysql -uapplication -papplication loopers < .docs/week5/sql/seed_likes.sql
# AS-IS①: likes(product_id) 인덱스 없이 join+group by 측정
# AS-IS②: CREATE INDEX idx_likes_product ON likes(product_id) 후 재측정
# TO-BE : SELECT * FROM products ORDER BY like_count DESC LIMIT 20
```
