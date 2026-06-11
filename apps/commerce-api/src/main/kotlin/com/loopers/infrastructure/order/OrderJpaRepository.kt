package com.loopers.infrastructure.order

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): OrderEntity?

    // 기간 경계가 null 이면 해당 절을 무력화한다 — BETWEEN 에 sentinel 시각을 넣지 않으므로
    // MySQL DATETIME 표현 범위(1000~9999) 밖 값으로 매칭이 비는 문제를 피한다. 경계는 모두 포함(inclusive).
    @Query(
        value = """
        SELECT o FROM OrderEntity o
        WHERE o.userId = :userId
          AND (:start IS NULL OR o.orderedAt >= :start)
          AND (:end IS NULL OR o.orderedAt <= :end)
        """,
        countQuery = """
        SELECT COUNT(o) FROM OrderEntity o
        WHERE o.userId = :userId
          AND (:start IS NULL OR o.orderedAt >= :start)
          AND (:end IS NULL OR o.orderedAt <= :end)
        """,
    )
    fun findAllByUserIdInPeriod(
        @Param("userId") userId: Long,
        @Param("start") start: LocalDateTime?,
        @Param("end") end: LocalDateTime?,
        pageable: Pageable,
    ): Page<OrderEntity>

    fun findAllBy(pageable: Pageable): Page<OrderEntity>
}
