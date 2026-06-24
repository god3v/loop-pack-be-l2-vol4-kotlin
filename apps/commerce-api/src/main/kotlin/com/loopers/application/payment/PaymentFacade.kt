package com.loopers.application.payment

import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
) {
    @Transactional
    fun pay(command: PaymentCommand): PaymentRequestResult {
        val order = orderRepository.findByIdForUpdate(command.orderId)
            ?: throw CoreException(OrderErrorType.ORDER_NOT_FOUND)
        // 본인 주문인지 검증
        order.validateOwnedBy(command.userId.toLong())
        // 이미 결제 진행/완료인지 검증
        order.validatePayable()
        // 결제 진행 상태 변경
        order.markPaymentPending()
        orderRepository.save(order)

        // 외부 PG 에 결제 요청 → 접수(거래 식별자 + 처리 중).
        val result = paymentGateway.request(
            PaymentRequestCommand(
                userId = command.userId,
                orderId = order.id,
                amount = order.totalAmount,
                cardType = command.cardType,
                cardNo = command.cardNo,
                callbackUrl = CALLBACK_URL,
            ),
        )
        // 접수된 거래 식별자를 결제에 기록·저장한다(상태는 REQUESTED — 확정은 콜백/폴링).
        val payment = Payment.request(orderId = order.id, amount = order.totalAmount)
        payment.accept(result.transactionKey)
        val saved = paymentRepository.save(payment)
        return PaymentRequestResult(
            paymentId = saved.id,
            orderId = order.id,
            status = saved.status,
            transactionKey = saved.transactionId,
            amount = saved.amount,
            requestedAt = saved.requestedAt,
        )
    }

    companion object {
        // TODO: 환경별 외부화(payment.callback-url). pg-simulator 는 callbackUrl 이 http://localhost:8080 으로 시작할 것을 요구한다.
        private const val CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback"
    }
}
