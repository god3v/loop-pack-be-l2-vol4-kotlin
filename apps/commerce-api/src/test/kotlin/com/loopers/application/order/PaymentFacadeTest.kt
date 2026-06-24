package com.loopers.application.order

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentRequestResult
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("PaymentFacade — 결제 요청(pay)")
class PaymentFacadeTest {
    private val orderRepository: OrderRepository = mockk(relaxed = true)
    private val paymentRepository: PaymentRepository = mockk()
    private val paymentGateway: PaymentGateway = mockk(relaxed = true)
    private val paymentFacade = PaymentFacade(orderRepository, paymentRepository, paymentGateway)

    private fun createdOrder() = Order(
        id = 1L,
        userId = 1L,
        lines = listOf(OrderLine.create(productId = 1L, productName = "운동화", unitPrice = 1000, quantity = 2)),
        orderedAt = LocalDateTime.now(),
        idempotencyKey = "k",
    )

    private fun command() =
        PaymentCommand(userId = "7", orderId = 1L, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451")

    @DisplayName("결제대기 주문에 결제를 요청하면 주문을 결제 진행으로 전이하고, 발급된 거래 식별자를 기록해 접수 정보를 반환한다.")
    @Test
    fun marksPendingRecordsTransactionKeyAndReturnsAcceptance() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder()
        every { paymentGateway.request(any()) } returns PaymentRequestResult("tx-key-1", PgTransactionStatus.PENDING)
        every { paymentRepository.save(any()) } returns
            Payment(id = 10L, orderId = 1L, amount = 2000L, transactionId = "tx-key-1", requestedAt = LocalDateTime.now())

        val info = paymentFacade.pay(command())

        assertThat(info.paymentId).isEqualTo(10L)
        assertThat(info.orderId).isEqualTo(1L)
        assertThat(info.status).isEqualTo(PaymentStatus.REQUESTED)
        assertThat(info.transactionKey).isEqualTo("tx-key-1")
        assertThat(info.amount).isEqualTo(2000L)
        verify { paymentGateway.request(any()) }
    }

    @DisplayName("이미 결제 진행/완료인 주문(결제 가능 상태 아님)에 결제를 요청하면 ORDER_NOT_PAYABLE 이 발생하고 외부 PG 를 호출하지 않는다.")
    @Test
    fun rejectsWhenOrderNotPayable() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder().also { it.markPaymentPending() }

        assertThrows<CoreException> { paymentFacade.pay(command()) }

        verify(exactly = 0) { paymentGateway.request(any()) }
    }

    @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 가 발생한다.")
    @Test
    fun rejectsWhenOrderMissing() {
        every { orderRepository.findByIdForUpdate(1L) } returns null

        assertThrows<CoreException> { paymentFacade.pay(command()) }

        verify(exactly = 0) { paymentGateway.request(any()) }
    }
}
