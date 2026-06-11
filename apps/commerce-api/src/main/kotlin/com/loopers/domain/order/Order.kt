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
    userCouponId: Long? = null,
    discountAmount: Long = 0L,
    status: OrderStatus = OrderStatus.PAYMENT_PENDING,
    paymentTransactionId: String? = null,
    paymentResultCode: String? = null,
) {
    /** 할인 적용 전 라인 합계(쿠폰 최소 주문 금액 판정 기준). */
    val originalAmount: Long get() = lines.sumOf { it.subtotal }

    /** 쿠폰 할인이 반영된 최종 결제 대상 금액. 음수가 될 수 없다. */
    val totalAmount: Long get() = (originalAmount - discountAmount).coerceAtLeast(0L)

    var userCouponId: Long? = userCouponId
        private set

    var discountAmount: Long = discountAmount
        private set

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
        when (status) {
            OrderStatus.PAID -> return
            OrderStatus.PAYMENT_FAILED ->
                throw CoreException(OrderErrorType.INVALID_PAYMENT_TRANSITION, "실패한 주문을 결제 완료로 전이할 수 없다.")
            OrderStatus.PAYMENT_PENDING -> {
                this.status = OrderStatus.PAID
                this.paymentTransactionId = transactionId
                this.paymentResultCode = resultCode
            }
        }
    }

    fun markPaymentFailed(transactionId: String?, resultCode: String?) {
        when (status) {
            OrderStatus.PAYMENT_FAILED -> return // 중복 콜백: 멱등 no-op
            OrderStatus.PAID ->
                throw CoreException(OrderErrorType.INVALID_PAYMENT_TRANSITION, "결제 완료된 주문을 실패로 전이할 수 없다.")
            OrderStatus.PAYMENT_PENDING -> {
                this.status = OrderStatus.PAYMENT_FAILED
                this.paymentTransactionId = transactionId
                this.paymentResultCode = resultCode
            }
        }
    }

    /**
     * 쿠폰을 주문에 적용한다 — 주문 1건당 1장만 적용되며, 적용된 발급 쿠폰 식별자와 계산된 할인 금액을 바인딩한다.
     * 할인 금액은 음수가 될 수 없고 라인 합계를 초과하지 않는다.
     */
    fun applyCoupon(userCouponId: Long, discountAmount: Long) {
        this.userCouponId = userCouponId
        this.discountAmount = discountAmount.coerceIn(0L, originalAmount)
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")

        fun create(
            userId: Long,
            lines: List<OrderLine>,
            idempotencyKey: String,
            userCouponId: Long? = null,
        ): Order = Order(
            userId = userId,
            lines = lines,
            orderedAt = LocalDateTime.now(SEOUL),
            idempotencyKey = idempotencyKey,
            userCouponId = userCouponId,
        )
    }
}
