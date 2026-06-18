package com.loopers.application.order.port

/**
 * 외부 결제 게이트웨이 — 주문 결제를 위임하는 outbound port.
 * 본 iteration 에서는 항상 성공하는 어댑터를 사용하며, 주문 저장 트랜잭션이 커밋된 뒤 호출된다.
 */
interface PaymentGateway {
    fun charge(orderId: Long, amount: Long): PaymentResult

    /** 승인된 결제를 환불(취소)한다 — 외부 거래 식별자로 멱등 처리한다(재시도 시 이중 환불 방지). */
    fun refund(transactionId: String, amount: Long): PaymentResult
}

/** 결제 결과 — 외부 게이트웨이의 응답 값 객체. 영속 애그리거트가 아니다. */
data class PaymentResult(
    val transactionId: String,
    val resultCode: String,
    val success: Boolean,
)
