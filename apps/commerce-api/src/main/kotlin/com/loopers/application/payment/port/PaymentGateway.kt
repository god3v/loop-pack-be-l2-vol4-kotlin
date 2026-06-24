package com.loopers.application.payment.port

/**
 * 외부 결제 게이트웨이 — 비동기 PG 에 결제를 위임하는 outbound port.
 * `request` 는 결과를 즉시 확정하지 않고 **접수(거래 식별자 + 처리 중)** 를 돌려받는다 — 확정은 콜백/폴링이 한다.
 */
interface PaymentGateway {
    fun request(request: PaymentRequestCommand): PaymentRequestResult
}

/** 결제 요청 입력 — 회원 식별자(X-USER-ID)·주문·금액·카드·결과 통지 주소. */
data class PaymentRequestCommand(
    val userId: String,
    val orderId: Long,
    val amount: Long,
    val cardType: String,
    val cardNo: String,
    val callbackUrl: String,
)

/** 결제 요청 접수 결과 — 외부 거래 식별자와 처리 상태(즉시 확정이 아니라 접수). */
data class PaymentRequestResult(
    val transactionKey: String,
    val status: PgTransactionStatus,
)

/** 외부 PG 의 처리 상태. PENDING(접수·처리 중) → SUCCESS/FAILED 로 비동기 확정된다. */
enum class PgTransactionStatus { PENDING, SUCCESS, FAILED }
