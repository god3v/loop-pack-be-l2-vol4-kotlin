package com.loopers.application.order

import com.loopers.application.order.port.PaymentResult
import com.loopers.application.payment.PaymentSettler
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("PaymentSettler — 결제 정산 단계")
class PaymentSettlerTest {
    private val paymentRepository: PaymentRepository = mockk()
    private val orderRepository: OrderRepository = mockk()
    private val orderCompensator: OrderCompensator = mockk(relaxed = true)
    private val settler = PaymentSettler(paymentRepository, orderRepository, orderCompensator)

    private fun pendingOrder() = Order.create(
        userId = 1L,
        lines = listOf(OrderLine.create(productId = 1L, productName = "운동화", unitPrice = 1000, quantity = 2)),
        idempotencyKey = "k",
    )

    @DisplayName("결제 성공 시")
    @Nested
    inner class OnSuccess {
        @DisplayName("결제는 APPROVED, 주문은 PAID 로 전이하고 보상은 일어나지 않는다.")
        @Test
        fun approvesAndMarksPaid() {
            val payment = Payment.request(orderId = 1L, amount = 2000L)
            val order = pendingOrder()
            every { paymentRepository.findByIdForUpdate(10L) } returns payment
            every { orderRepository.findByIdForUpdate(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderRepository.save(any()) } answers { firstArg() }

            settler.settle(10L, PaymentResult("tx-1", "APPROVED", true))

            assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(payment.transactionId).isEqualTo("tx-1")
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
            verify(exactly = 0) { orderCompensator.restore(any()) }
        }
    }

    @DisplayName("결제 실패 시")
    @Nested
    inner class OnFailure {
        @DisplayName("결제는 FAILED, 주문은 PAYMENT_FAILED 로 전이하고 보상(재고·쿠폰 원복)을 수행한다.")
        @Test
        fun failsCompensatesAndMarksFailed() {
            val payment = Payment.request(orderId = 1L, amount = 2000L)
            val order = pendingOrder()
            every { paymentRepository.findByIdForUpdate(10L) } returns payment
            every { orderRepository.findByIdForUpdate(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderRepository.save(any()) } answers { firstArg() }

            settler.settle(10L, PaymentResult("tx-f", "DECLINED", false))

            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureReason).isEqualTo("DECLINED")
            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            verify { orderCompensator.restore(order) }
        }
    }

    @DisplayName("멱등 / 방어")
    @Nested
    inner class Idempotency {
        @DisplayName("REQUESTED 가 아닌 결제는 다시 정산하지 않는다 (중복 정산 no-op).")
        @Test
        fun skipsWhenNotRequested() {
            val payment = Payment.request(orderId = 1L, amount = 2000L)
                .also { it.approve("tx-prev", LocalDateTime.now()) }
            every { paymentRepository.findByIdForUpdate(10L) } returns payment

            settler.settle(10L, PaymentResult("tx", "APPROVED", true))

            verify(exactly = 0) { orderRepository.findByIdForUpdate(any()) }
            verify(exactly = 0) { paymentRepository.save(any()) }
        }

        @DisplayName("결제가 없으면 정산하지 않는다.")
        @Test
        fun skipsWhenPaymentMissing() {
            every { paymentRepository.findByIdForUpdate(404L) } returns null

            settler.settle(404L, PaymentResult("tx", "APPROVED", true))

            verify(exactly = 0) { orderRepository.findByIdForUpdate(any()) }
        }
    }
}
