package com.loopers.support.page

/**
 * 페이지 단위 조회의 입력 파라미터를 표현하는 프레임워크 독립 값 객체.
 *
 * [PageResult] 의 입력 짝이며, interfaces 계층이 Spring `Pageable` 을 본 타입으로 변환해 전달한다.
 * 조건 검색이 필요한 유즈케이스는 자신의 Query 안에 본 타입을 합성해 페이징 어휘를 공유한다.
 */
data class PageQuery(
    val page: Int,
    val size: Int,
)
