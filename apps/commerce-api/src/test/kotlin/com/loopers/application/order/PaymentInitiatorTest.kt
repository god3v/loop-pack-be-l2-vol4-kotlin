package com.loopers.application.order

import com.loopers.application.payment.PaymentInitiator
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PaymentInitiator — 결제 요청(REQUESTED) 단계")
class PaymentInitiatorTest {
    private val orderRepository: OrderRepository = mockk()
    private val paymentRepository: PaymentRepository = mockk()
    private val initiator = PaymentInitiator(orderRepository, paymentRepository)

    private fun pendingOrder() = Order.create(
        userId = 1L,
        lines = listOf(OrderLine.create(productId = 1L, productName = "운동화", unitPrice = 1000, quantity = 2)),
        idempotencyKey = "k",
    )

    @DisplayName("주문이 결제 대기이고 진행 중 결제가 없으면 totalAmount 로 REQUESTED 결제를 생성한다.")
    @Test
    fun createsRequestedWhenPendingAndNoActivePayment() {
        val order = pendingOrder()
        every { orderRepository.findByIdForUpdate(1L) } returns order
        every { paymentRepository.findLatestByOrderId(1L) } returns null
        every { paymentRepository.save(any()) } answers { firstArg() }

        val payment = initiator.request(1L)

        assertThat(payment).isNotNull()
        val created = requireNotNull(payment)
        assertThat(created.status).isEqualTo(PaymentStatus.REQUESTED)
        assertThat(created.orderId).isEqualTo(1L)
        assertThat(created.amount).isEqualTo(order.totalAmount)
        verify { paymentRepository.save(any()) }
    }

    @DisplayName("주문이 결제 대기 상태가 아니면 결제를 만들지 않는다.")
    @Test
    fun skipsWhenOrderNotPending() {
        val order = pendingOrder().also { it.markPaid("tx", "APPROVED") }
        every { orderRepository.findByIdForUpdate(1L) } returns order

        val payment = initiator.request(1L)

        assertThat(payment).isNull()
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @DisplayName("이미 진행 중(REQUESTED/APPROVED)인 결제가 있으면 중복 생성하지 않는다.")
    @Test
    fun skipsWhenActivePaymentExists() {
        val order = pendingOrder()
        every { orderRepository.findByIdForUpdate(1L) } returns order
        every { paymentRepository.findLatestByOrderId(1L) } returns Payment.request(orderId = 1L, amount = 2000L)

        val payment = initiator.request(1L)

        assertThat(payment).isNull()
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @DisplayName("주문이 없으면 결제를 만들지 않는다.")
    @Test
    fun skipsWhenOrderMissing() {
        every { orderRepository.findByIdForUpdate(99L) } returns null

        val payment = initiator.request(99L)

        assertThat(payment).isNull()
    }
}
