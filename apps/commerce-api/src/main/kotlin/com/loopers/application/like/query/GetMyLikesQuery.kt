package com.loopers.application.like.query

import com.loopers.support.page.PageQuery

/**
 * '내 좋아요 목록 조회' 유즈케이스의 입력 Query.
 */
data class GetMyLikesQuery(
    val userId: Long,
    val paging: PageQuery,
)
