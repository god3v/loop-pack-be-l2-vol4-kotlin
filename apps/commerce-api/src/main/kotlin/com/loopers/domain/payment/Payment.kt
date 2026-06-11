package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import java.time.LocalDateTime

/**
 * 결제 애그리거트 — 결제 생애주기(요청/완료/실패/취소)의 source of truth.
 * 외부 PG 호출 *전에* `REQUESTED` 로 먼저 영속된 뒤, 응답에 따라 `APPROVED`/`FAILED` 로,
 * 사후 환불 시 `CANCELED` 로 전이한다. 상태 전이 규칙은 도메인이 캡슐화한다(Tell, Don't Ask).
 *
 * 멱등키는 별도로 두지 않는다 — 외부 호출의 멱등 레퍼런스는 주문 식별자/주문 멱등키를 재사용한다(1주문 1결제).
 */
class Payment internal constructor(
    val id: Long = 0L,
    val orderId: Long,
    val amount: Long,
    status: PaymentStatus = PaymentStatus.REQUESTED,
    transactionId: String? = null,
    failureReason: String? = null,
    val requestedAt: LocalDateTime,
    paidAt: LocalDateTime? = null,
    canceledAt: LocalDateTime? = null,
) {
    var status: PaymentStatus = status
        private set

    var transactionId: String? = transactionId
        private set

    var failureReason: String? = failureReason
        private set

    var paidAt: LocalDateTime? = paidAt
        private set

    var canceledAt: LocalDateTime? = canceledAt
        private set

    /** PG 승인 반영 — REQUESTED 에서만 가능하다. 외부 거래 식별자와 결제 시각을 기록한다. */
    fun approve(transactionId: String, at: LocalDateTime) {
        if (status != PaymentStatus.REQUESTED) {
            throw CoreException(PaymentErrorType.INVALID_PAYMENT_TRANSITION, "요청 상태의 결제만 승인할 수 있다.")
        }
        status = PaymentStatus.APPROVED
        this.transactionId = transactionId
        paidAt = at
    }

    /** PG 거절 반영 — 멱등(이미 FAILED 면 no-op). 승인·취소된 결제는 실패로 뒤집을 수 없다. */
    fun fail(failureReason: String?) {
        when (status) {
            PaymentStatus.FAILED -> return
            PaymentStatus.REQUESTED -> {
                status = PaymentStatus.FAILED
                this.failureReason = failureReason
            }
            PaymentStatus.APPROVED, PaymentStatus.CANCELED ->
                throw CoreException(PaymentErrorType.INVALID_PAYMENT_TRANSITION, "승인·취소된 결제는 실패로 전이할 수 없다.")
        }
    }

    /** 취소 — REQUESTED(청구 전, 환불 불필요)·APPROVED(환불) 에서 가능하다. 멱등. */
    fun cancel(at: LocalDateTime) {
        when (status) {
            PaymentStatus.CANCELED -> return
            PaymentStatus.REQUESTED, PaymentStatus.APPROVED -> {
                status = PaymentStatus.CANCELED
                canceledAt = at
            }
            PaymentStatus.FAILED ->
                throw CoreException(PaymentErrorType.INVALID_PAYMENT_TRANSITION, "실패한 결제는 취소할 수 없다.")
        }
    }

    fun isRequested(): Boolean = status == PaymentStatus.REQUESTED

    fun isApproved(): Boolean = status == PaymentStatus.APPROVED

    companion object {
        fun request(orderId: Long, amount: Long): Payment = Payment(
            orderId = orderId,
            amount = amount,
            status = PaymentStatus.REQUESTED,
            requestedAt = LocalDateTime.now(),
        )
    }
}
