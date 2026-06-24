package com.loopers.application.order

import com.loopers.application.order.port.PaymentGateway
import com.loopers.application.order.port.PaymentResult
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.PaymentInitiator
import com.loopers.application.payment.PaymentSettler
import com.loopers.domain.payment.Payment
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("PaymentFacade — 결제 오케스트레이션")
class PaymentFacadeTest {
    private val paymentInitiator: PaymentInitiator = mockk()
    private val paymentSettler: PaymentSettler = mockk(relaxed = true)
    private val paymentGateway: PaymentGateway = mockk(relaxed = true)
    private val paymentFacade = PaymentFacade(paymentInitiator, paymentSettler, paymentGateway)

    @DisplayName("결제 — 요청 → 락 밖 charge → 정산")
    @Nested
    inner class Pay {
        @DisplayName("요청이 성사되면 결제 금액으로 외부 결제를 호출하고 그 결과로 정산한다.")
        @Test
        fun requestsThenChargesThenSettles() {
            val payment = Payment(id = 10L, orderId = 1L, amount = 2000L, requestedAt = LocalDateTime.now())
            every { paymentInitiator.request(1L) } returns payment
            every { paymentGateway.charge(1L, 2000L) } returns PaymentResult("tx-1", "APPROVED", true)

            paymentFacade.pay(1L)

            verify { paymentGateway.charge(1L, 2000L) }
            verify { paymentSettler.settle(10L, match { it.success }) }
        }

        @DisplayName("요청이 성사되지 않으면(이미 처리/진행 중) 외부 결제도 정산도 하지 않는다.")
        @Test
        fun skipsWhenRequestDeclined() {
            every { paymentInitiator.request(1L) } returns null

            paymentFacade.pay(1L)

            verify(exactly = 0) { paymentGateway.charge(any(), any()) }
            verify(exactly = 0) { paymentSettler.settle(any(), any()) }
        }
    }
}
