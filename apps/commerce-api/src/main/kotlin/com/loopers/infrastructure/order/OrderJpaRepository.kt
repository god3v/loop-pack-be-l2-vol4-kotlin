package com.loopers.infrastructure.order

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): OrderEntity?

    fun findAllByUserIdAndOrderedAtBetween(
        userId: Long,
        start: LocalDateTime,
        end: LocalDateTime,
        pageable: Pageable,
    ): List<OrderEntity>

    fun findAllBy(pageable: Pageable): List<OrderEntity>
}
