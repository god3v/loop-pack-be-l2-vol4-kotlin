package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorType
import com.loopers.support.error.CoreException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun save(order: Order): Order {
        val entity = if (order.id == 0L) {
            OrderEntity.from(order)
        } else {
            orderJpaRepository.findById(order.id)
                .orElseThrow { CoreException(OrderErrorType.ORDER_NOT_FOUND) }
                .apply { syncFrom(order) }
        }
        return orderJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Order? =
        orderJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): Order? =
        orderJpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.toDomain()

    override fun findAllByUserIdInPeriod(
        userId: Long,
        start: LocalDateTime?,
        end: LocalDateTime?,
        page: Int,
        size: Int,
    ): List<Order> = orderJpaRepository.findAllByUserIdInPeriod(
        userId,
        start,
        end,
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderedAt")),
    ).map { it.toDomain() }

    override fun findAllForAdmin(page: Int, size: Int): List<Order> =
        orderJpaRepository.findAllBy(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderedAt")),
        ).map { it.toDomain() }
}
