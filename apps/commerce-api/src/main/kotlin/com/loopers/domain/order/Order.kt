package com.loopers.domain.order

import com.loopers.support.error.CoreException
import java.time.LocalDateTime
import java.time.ZoneId

class Order internal constructor(
    val id: Long = 0L,
    val userId: Long,
    val lines: List<OrderLine>,
    val orderedAt: LocalDateTime,
    val idempotencyKey: String,
    val couponId: Long? = null,
    status: OrderStatus = OrderStatus.PAYMENT_PENDING,
    paymentTransactionId: String? = null,
    paymentResultCode: String? = null,
) {
    val totalAmount: Long get() = lines.sumOf { it.subtotal }

    var status: OrderStatus = status
        private set

    var paymentTransactionId: String? = paymentTransactionId
        private set

    var paymentResultCode: String? = paymentResultCode
        private set

    init {
        if (lines.isEmpty()) {
            throw CoreException(OrderErrorType.EMPTY_LINES, "주문에는 1개 이상의 라인이 필요하다.")
        }
        if (idempotencyKey.isBlank()) {
            throw CoreException(OrderErrorType.IDEMPOTENCY_KEY_BLANK, "idempotencyKey 는 blank 일 수 없다.")
        }
    }

    fun markPaid(transactionId: String, resultCode: String) {
        this.status = OrderStatus.PAID
        this.paymentTransactionId = transactionId
        this.paymentResultCode = resultCode
    }

    fun markPaymentFailed(transactionId: String?, resultCode: String?) {
        this.status = OrderStatus.PAYMENT_FAILED
        this.paymentTransactionId = transactionId
        this.paymentResultCode = resultCode
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")

        fun create(
            userId: Long,
            lines: List<OrderLine>,
            idempotencyKey: String,
            couponId: Long? = null,
        ): Order = Order(
            userId = userId,
            lines = lines,
            orderedAt = LocalDateTime.now(SEOUL),
            idempotencyKey = idempotencyKey,
            couponId = couponId,
        )
    }
}
