package com.loopers.infrastructure.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PaymentGatewayImpl")
class PaymentGatewayImplTest {
    private val gateway = PaymentGatewayImpl()

    @DisplayName("production impl 은 항상 success=true 를 반환한다.")
    @Test
    fun alwaysSucceeds() {
        val result = gateway.charge(orderId = 1L, amount = 50_000)

        assertThat(result.success).isTrue()
        assertThat(result.transactionId).startsWith("tx-")
        assertThat(result.resultCode).isEqualTo("APPROVED")
    }
}
