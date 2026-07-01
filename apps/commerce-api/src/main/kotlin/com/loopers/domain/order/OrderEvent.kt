package com.loopers.domain.order

import com.loopers.support.event.ExternalEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 도메인 이벤트 — 발생한 사실을 타입으로 구분한다.
 * 핵심 주문 트랜잭션과 분리된 부가 처리(알림·데이터 플랫폼 전송·판매량 집계)가 이 이벤트를 소비한다.
 * 시스템 경계를 넘어 전파되므로 `ExternalEvent`(→ Outbox) 다.
 */
sealed class OrderEvent : ExternalEvent {
    abstract val orderId: Long
    override val aggregateType: String get() = "ORDER"
    override val aggregateId: String get() = orderId.toString()

    data class Created(
        override val orderId: Long,
        val userId: Long,
        val totalAmount: Long,
        val lines: List<Line>,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : OrderEvent() {
        override val eventType: String get() = "ORDER_CREATED"

        data class Line(val productId: Long, val quantity: Int)

        companion object {
            fun from(order: Order): Created = Created(
                orderId = order.id,
                userId = order.userId,
                totalAmount = order.totalAmount,
                lines = order.lines.map { Line(productId = it.productId, quantity = it.quantity.value) },
            )
        }
    }
}
