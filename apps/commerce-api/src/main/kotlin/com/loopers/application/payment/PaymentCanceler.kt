package com.loopers.application.payment

import com.loopers.application.order.OrderCompensator
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 결제 취소 단계 — 결제를 비관 락으로 잡아 CANCELED 가 아닐 때만 1회 처리한다(멱등).
 * 결제·주문을 CANCELED 로 전이하고 보상(차감 재고·소진 쿠폰 원복) 한다. 외부 PG 환불은 호출자(PaymentFacade)가 락 밖에서 먼저 수행한다.
 */
@Component
class PaymentCanceler(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val orderCompensator: OrderCompensator,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun cancel(paymentId: Long) {
        val payment = paymentRepository.findByIdForUpdate(paymentId) ?: return
        if (payment.status == PaymentStatus.CANCELED) return
        val order = orderRepository.findByIdForUpdate(payment.orderId) ?: return

        payment.cancel(LocalDateTime.now())
        order.cancel()
        orderCompensator.restore(order)
        paymentRepository.save(payment)
        orderRepository.save(order)
    }
}
