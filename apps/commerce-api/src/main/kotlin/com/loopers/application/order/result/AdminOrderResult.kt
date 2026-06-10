package com.loopers.application.order.result

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.user.User
import java.time.LocalDateTime

data class AdminOrderResult(
    val orderId: Long,
    val userId: Long,
    val userMaskedName: String,
    val orderedAt: LocalDateTime,
    val totalAmount: Long,
    val discountAmount: Long,
    val userCouponId: Long?,
    val status: OrderStatus,
    val paymentTransactionId: String?,
    val paymentResultCode: String?,
    val lines: List<OrderLineResult>,
) {
    companion object {
        fun of(order: Order, user: User): AdminOrderResult = AdminOrderResult(
            orderId = order.id,
            userId = order.userId,
            userMaskedName = user.name(),
            orderedAt = order.orderedAt,
            totalAmount = order.totalAmount,
            discountAmount = order.discountAmount,
            userCouponId = order.userCouponId,
            status = order.status,
            paymentTransactionId = order.paymentTransactionId,
            paymentResultCode = order.paymentResultCode,
            lines = order.lines.map { OrderLineResult.from(it) },
        )
    }
}
