package com.loopers.domain.order

import java.time.LocalDateTime

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): Order?
    fun findAllByUserIdAndOrderedAtBetween(
        userId: Long,
        start: LocalDateTime,
        end: LocalDateTime,
        page: Int,
        size: Int,
    ): List<Order>

    fun findAllForAdmin(page: Int, size: Int): List<Order>
}
