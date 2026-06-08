package com.loopers.support.page

/**
 * 페이지 단위 조회 결과를 표현하는 프레임워크 독립 값 객체.
 *
 * Spring Data 의 `Page` 는 infrastructure(adapter) 에서 본 타입으로 변환되며,
 * application·domain 은 프레임워크를 알지 못한 채 본 타입으로만 페이징을 주고받는다.
 */
data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
