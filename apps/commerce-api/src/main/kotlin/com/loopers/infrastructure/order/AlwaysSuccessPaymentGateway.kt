package com.loopers.infrastructure.order

import com.loopers.application.order.port.PaymentGateway
import com.loopers.application.order.port.PaymentResult
import org.springframework.stereotype.Component

/**
 * 본 iteration 의 최소 결제 어댑터 — 항상 성공한다.
 * 결제 실패·보상은 차주 과제(requirements §미해결).
 */
@Component
class AlwaysSuccessPaymentGateway : PaymentGateway {
    override fun charge(orderId: Long, amount: Long): PaymentResult =
        PaymentResult(transactionId = "tx-$orderId", resultCode = "APPROVED", success = true)
}
