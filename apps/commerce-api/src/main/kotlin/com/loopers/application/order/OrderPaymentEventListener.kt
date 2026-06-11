package com.loopers.application.order

import com.loopers.application.order.port.PaymentGateway
import com.loopers.domain.order.OrderRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 저장 트랜잭션이 커밋된 뒤(AFTER_COMMIT) 결제를 처리한다 — 외부 결제 호출을 주문 트랜잭션 밖으로 분리한다.
 * 별도 트랜잭션(REQUIRES_NEW)에서 결제 결과를 주문에 반영(PAID 전이)한다.
 * 본 iteration 의 결제는 항상 성공하므로 실패 분기는 발생하지 않는다(차주: 결제 실패 시 보상·PAYMENT_FAILED).
 */
@Component
class OrderPaymentEventListener(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onOrderPlaced(event: OrderPlacedEvent) {
        val order = orderRepository.findById(event.orderId) ?: return
        val result = paymentGateway.charge(order.id, order.totalAmount)
        if (result.success) {
            order.markPaid(result.transactionId, result.resultCode)
        } else {
            order.markPaymentFailed(result.transactionId, result.resultCode)
        }
        orderRepository.save(order)
    }
}
