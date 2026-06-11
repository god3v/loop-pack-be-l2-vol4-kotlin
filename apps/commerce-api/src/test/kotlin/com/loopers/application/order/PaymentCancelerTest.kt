package com.loopers.application.order

import com.loopers.application.payment.PaymentCanceler
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
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("PaymentCanceler — 결제 취소 정산")
class PaymentCancelerTest {
    private val paymentRepository: PaymentRepository = mockk()
    private val orderRepository: OrderRepository = mockk()
    private val orderCompensator: OrderCompensator = mockk(relaxed = true)
    private val canceler = PaymentCanceler(paymentRepository, orderRepository, orderCompensator)

    private fun paidOrder() = Order.create(
        userId = 1L,
        lines = listOf(OrderLine.create(productId = 1L, productName = "운동화", unitPrice = 1000, quantity = 2)),
        idempotencyKey = "k",
    ).also { it.markPaid("tx-1", "APPROVED") }

    @DisplayName("승인된 결제를 취소하면 결제·주문이 CANCELED 로 전이하고 보상을 수행한다.")
    @Test
    fun cancelsApprovedPaymentAndOrder() {
        val payment = Payment.request(orderId = 1L, amount = 2000L).also { it.approve("tx-1", LocalDateTime.now()) }
        val order = paidOrder()
        every { paymentRepository.findByIdForUpdate(10L) } returns payment
        every { orderRepository.findByIdForUpdate(1L) } returns order
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { orderRepository.save(any()) } answers { firstArg() }

        canceler.cancel(10L)

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
        verify { orderCompensator.restore(order) }
    }

    @DisplayName("이미 CANCELED 인 결제는 다시 취소하지 않는다 (멱등 no-op).")
    @Test
    fun skipsWhenAlreadyCanceled() {
        val payment = Payment.request(orderId = 1L, amount = 2000L).also { it.cancel(LocalDateTime.now()) }
        every { paymentRepository.findByIdForUpdate(10L) } returns payment

        canceler.cancel(10L)

        verify(exactly = 0) { orderRepository.findByIdForUpdate(any()) }
        verify(exactly = 0) { orderCompensator.restore(any()) }
    }

    @DisplayName("결제가 없으면 취소하지 않는다.")
    @Test
    fun skipsWhenPaymentMissing() {
        every { paymentRepository.findByIdForUpdate(404L) } returns null

        canceler.cancel(404L)

        verify(exactly = 0) { orderRepository.findByIdForUpdate(any()) }
    }
}
