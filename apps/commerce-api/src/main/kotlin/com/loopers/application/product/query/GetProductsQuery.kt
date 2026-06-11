package com.loopers.application.product.query

import com.loopers.domain.product.ProductSortType
import com.loopers.support.page.PageQuery

/**
 * '회원 상품 목록 조회'(UC-1) 유즈케이스의 입력 Query.
 *
 * 정렬·브랜드 필터와 페이징을 함께 받는다 — 페이징 어휘는 [PageQuery] 를 합성해 공유한다.
 * `sort` 가 null 이면 Facade 에서 [ProductSortType.LATEST] 로 해석한다.
 */
data class GetProductsQuery(
    val sort: ProductSortType?,
    val brandId: Long?,
    val paging: PageQuery,
)
