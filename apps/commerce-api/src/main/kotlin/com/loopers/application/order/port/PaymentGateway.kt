package com.loopers.application.order.port

interface PaymentGateway {
    fun charge(orderId: Long, amount: Int): PaymentResult
}

data class PaymentResult(
    val success: Boolean,
    val transactionId: String?,
    val resultCode: String?,
)
