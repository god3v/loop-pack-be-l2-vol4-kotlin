package com.loopers.application.payment

import com.loopers.application.order.OrderCompensator
import com.loopers.application.order.port.PaymentResult
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.PaymentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 결제 정산 단계 — 외부 charge 결과를 결제·주문에 반영한다. 결제를 비관 락으로 잡아 REQUESTED 일 때만 1회 처리한다(중복 정산 멱등).
 *
 * 성공이면 결제 APPROVED + 주문 PAID, 실패면 결제 FAILED + 보상(차감 재고·소진 쿠폰 원복) + 주문 PAYMENT_FAILED.
 * 도메인 상태 전이는 각 애그리거트가 소유하고, 본 클래스는 트랜잭션 경계와 순서만 조율한다.
 */
@Component
class PaymentSettler(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val orderCompensator: OrderCompensator,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun settle(paymentId: Long, result: PaymentResult) {
        val payment = paymentRepository.findByIdForUpdate(paymentId) ?: return
        if (!payment.isRequested()) return
        val order = orderRepository.findByIdForUpdate(payment.orderId) ?: return

        if (result.success) {
            payment.approve(result.transactionId, LocalDateTime.now())
            order.markPaid(result.transactionId, result.resultCode)
        } else {
            payment.fail(result.resultCode)
            orderCompensator.restore(order)
            order.markPaymentFailed(result.transactionId, result.resultCode)
        }
        paymentRepository.save(payment)
        orderRepository.save(order)
    }
}
