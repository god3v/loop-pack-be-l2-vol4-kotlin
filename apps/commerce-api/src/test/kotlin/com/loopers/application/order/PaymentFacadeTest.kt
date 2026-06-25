package com.loopers.application.order

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentGatewayException
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("PaymentFacade — 결제 요청(pay)·정산(settle)")
class PaymentFacadeTest {
    private val orderRepository: OrderRepository = mockk(relaxed = true)
    private val paymentRepository: PaymentRepository = mockk()
    private val paymentGateway: PaymentGateway = mockk(relaxed = true)
    private val orderCompensator: OrderCompensator = mockk(relaxed = true)
    private val paymentFacade = PaymentFacade(orderRepository, paymentRepository, paymentGateway, orderCompensator)

    private fun createdOrder() = Order(
        id = 1L,
        userId = 1L,
        lines = listOf(OrderLine.create(productId = 1L, productName = "운동화", unitPrice = 1000, quantity = 2)),
        orderedAt = LocalDateTime.now(),
        idempotencyKey = "k",
    )

    // 주문 소유자(userId=1L)와 같은 회원이 결제를 요청하는 정상 케이스.
    private fun command() =
        PaymentCommand(userId = "1", orderId = 1L, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451")

    @DisplayName("결제대기 주문에 결제를 요청하면 주문을 결제 진행으로 전이하고, 발급된 거래 식별자를 기록해 접수 정보를 반환한다.")
    @Test
    fun marksPendingRecordsTransactionKeyAndReturnsAcceptance() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder()
        every { paymentGateway.request(any()) } returns PaymentResponse("tx-key-1", PgTransactionStatus.PENDING)
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

    @DisplayName("외부 PG 가 일시 장애(PaymentGatewayException)면 결제를 REQUESTED(거래 식별자 없음)로 두고 접수 응답을 반환한다 — 예외를 전파하지 않는다.")
    @Test
    fun fallsBackToRequestedOnGatewayFailure() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder()
        every { paymentGateway.request(any()) } throws PaymentGatewayException("타임아웃")
        every { paymentRepository.save(any()) } returns
            Payment(id = 10L, orderId = 1L, amount = 2000L, requestedAt = LocalDateTime.now())

        val info = paymentFacade.pay(command())

        assertThat(info.paymentId).isEqualTo(10L)
        assertThat(info.status).isEqualTo(PaymentStatus.REQUESTED)
        assertThat(info.transactionKey).isNull()
        verify { paymentGateway.request(any()) }
    }

    @DisplayName("이미 결제 진행/완료인 주문(결제 가능 상태 아님)에 결제를 요청하면 ORDER_NOT_PAYABLE 이 발생하고 외부 PG 를 호출하지 않는다.")
    @Test
    fun rejectsWhenOrderNotPayable() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder().also { it.markPaymentPending() }

        assertThrows<CoreException> { paymentFacade.pay(command()) }

        verify(exactly = 0) { paymentGateway.request(any()) }
    }

    @DisplayName("타인 소유 주문에 결제를 요청하면 ORDER_FORBIDDEN 이 발생하고 외부 PG 를 호출하지 않는다.")
    @Test
    fun rejectsWhenOrderOwnedByAnother() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder() // 주문 소유자 = 1L

        val ex = assertThrows<CoreException> {
            paymentFacade.pay(
                PaymentCommand(userId = "999", orderId = 1L, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451"),
            )
        }

        assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_FORBIDDEN)
        verify(exactly = 0) { paymentGateway.request(any()) }
    }

    @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 가 발생한다.")
    @Test
    fun rejectsWhenOrderMissing() {
        every { orderRepository.findByIdForUpdate(1L) } returns null

        assertThrows<CoreException> { paymentFacade.pay(command()) }

        verify(exactly = 0) { paymentGateway.request(any()) }
    }

    @DisplayName("정산(settle) 은, ")
    @Nested
    inner class Settle {
        private fun pendingOrder() = createdOrder().also { it.markPaymentPending() }

        @DisplayName("외부 결과가 성공이면 결제는 APPROVED, 주문은 PAID 로 전이한다.")
        @Test
        fun approvesAndMarksPaidOnSuccess() {
            val payment = Payment(id = 10L, orderId = 1L, amount = 2000L, transactionId = "tx-1", requestedAt = LocalDateTime.now())
            val order = pendingOrder()
            every { paymentRepository.findByTransactionIdForUpdate("tx-1") } returns payment
            every { orderRepository.findByIdForUpdate(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderRepository.save(any()) } answers { firstArg() }

            paymentFacade.settle(PgTransaction("tx-1", PgTransactionStatus.SUCCESS, null))

            assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(payment.transactionId).isEqualTo("tx-1")
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
            verify(exactly = 0) { orderCompensator.restore(any()) }
        }

        @DisplayName("외부 결과가 실패면 결제는 FAILED, 주문은 PAYMENT_FAILED 로 전이하고 보상(재고·쿠폰 원복)을 수행한다.")
        @Test
        fun failsCompensatesAndMarksFailedOnFailure() {
            val payment = Payment(id = 10L, orderId = 1L, amount = 2000L, transactionId = "tx-f", requestedAt = LocalDateTime.now())
            val order = pendingOrder()
            every { paymentRepository.findByTransactionIdForUpdate("tx-f") } returns payment
            every { orderRepository.findByIdForUpdate(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderRepository.save(any()) } answers { firstArg() }

            paymentFacade.settle(PgTransaction("tx-f", PgTransactionStatus.FAILED, "DECLINED"))

            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureReason).isEqualTo("DECLINED")
            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            verify { orderCompensator.restore(order) }
        }

        @DisplayName("이미 정산된(REQUESTED 가 아닌) 결제에 결과를 다시 반영하면 멱등하게 무시한다 — 주문 조회·저장·보상이 일어나지 않는다.")
        @Test
        fun ignoresWhenAlreadySettled() {
            val payment = Payment(
                id = 10L,
                orderId = 1L,
                amount = 2000L,
                status = PaymentStatus.APPROVED,
                transactionId = "tx-1",
                requestedAt = LocalDateTime.now(),
            )
            every { paymentRepository.findByTransactionIdForUpdate("tx-1") } returns payment

            paymentFacade.settle(PgTransaction("tx-1", PgTransactionStatus.SUCCESS, null))

            verify(exactly = 0) { orderRepository.findByIdForUpdate(any()) }
            verify(exactly = 0) { paymentRepository.save(any()) }
            verify(exactly = 0) { orderCompensator.restore(any()) }
        }

        @DisplayName("알 수 없는 거래 식별자의 결과 통지는 정산 없이 무시한다 — 주문 조회·저장·보상이 일어나지 않는다.")
        @Test
        fun ignoresUnknownTransaction() {
            every { paymentRepository.findByTransactionIdForUpdate("unknown") } returns null

            paymentFacade.settle(PgTransaction("unknown", PgTransactionStatus.SUCCESS, null))

            verify(exactly = 0) { orderRepository.findByIdForUpdate(any()) }
            verify(exactly = 0) { paymentRepository.save(any()) }
            verify(exactly = 0) { orderCompensator.restore(any()) }
        }
    }
}
