package com.loopers.infrastructure.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.BatchSize
import java.time.LocalDateTime

@Entity
@Table(
    name = "orders",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_orders_user_idem", columnNames = ["user_id", "idempotency_key"]),
    ],
)
class OrderEntity private constructor(
    userId: Long,
    orderedAt: LocalDateTime,
    idempotencyKey: String,
    couponId: Long?,
    status: OrderStatus,
    paymentTransactionId: String?,
    paymentResultCode: String?,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "ordered_at", nullable = false)
    var orderedAt: LocalDateTime = orderedAt
        protected set

    @Column(name = "idempotency_key", nullable = false)
    var idempotencyKey: String = idempotencyKey
        protected set

    @Column(name = "coupon_id")
    var couponId: Long? = couponId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus = status
        protected set

    @Column(name = "payment_transaction_id")
    var paymentTransactionId: String? = paymentTransactionId
        protected set

    @Column(name = "payment_result_code")
    var paymentResultCode: String? = paymentResultCode
        protected set

    // 목록 조회 시 주문별 lines 지연 로딩이 N+1 이 되지 않도록, 페이지의 주문들을 모아
    // IN 절로 한 번에 적재한다 (페이징과 호환 — fetch join 과 달리 메모리 페이징을 유발하지 않음).
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    @BatchSize(size = 100)
    var lines: MutableList<OrderLineEntity> = mutableListOf()
        protected set

    fun toDomain(): Order = Order(
        id = this.id,
        userId = this.userId,
        lines = this.lines.map { it.toDomain() },
        orderedAt = this.orderedAt,
        idempotencyKey = this.idempotencyKey,
        couponId = this.couponId,
        status = this.status,
        paymentTransactionId = this.paymentTransactionId,
        paymentResultCode = this.paymentResultCode,
    )

    fun syncFrom(order: Order) {
        this.status = order.status
        this.paymentTransactionId = order.paymentTransactionId
        this.paymentResultCode = order.paymentResultCode
    }

    companion object {
        fun from(order: Order): OrderEntity {
            val entity = OrderEntity(
                userId = order.userId,
                orderedAt = order.orderedAt,
                idempotencyKey = order.idempotencyKey,
                couponId = order.couponId,
                status = order.status,
                paymentTransactionId = order.paymentTransactionId,
                paymentResultCode = order.paymentResultCode,
            )
            order.lines.forEach { line ->
                entity.lines.add(OrderLineEntity.from(line, entity))
            }
            return entity
        }
    }
}
