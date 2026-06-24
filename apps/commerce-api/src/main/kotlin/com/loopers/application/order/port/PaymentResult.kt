package com.loopers.application.order.port

/**
 * 결제 결과 값 객체 — 정산(`PaymentSettler`) 입력. 영속 애그리거트가 아니다.
 * (구 동기 게이트웨이의 응답 타입이었고, 콜백 정산 도입 시 외부 거래 상태 기반으로 재정의될 수 있다.)
 */
data class PaymentResult(
    val transactionId: String,
    val resultCode: String,
    val success: Boolean,
)
