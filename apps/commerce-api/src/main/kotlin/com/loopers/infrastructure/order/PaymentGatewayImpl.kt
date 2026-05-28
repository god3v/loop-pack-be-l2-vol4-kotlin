package com.loopers.infrastructure.order

import com.loopers.application.order.port.PaymentGateway
import com.loopers.application.order.port.PaymentResult
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PaymentGatewayImpl : PaymentGateway {
    override fun charge(orderId: Long, amount: Int): PaymentResult = PaymentResult(
        success = true,
        transactionId = "tx-${UUID.randomUUID()}",
        resultCode = "APPROVED",
    )
}
