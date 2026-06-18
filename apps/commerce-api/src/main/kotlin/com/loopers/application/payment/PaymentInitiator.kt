package com.loopers.application.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 요청 단계 — 외부 PG 호출 *전에* REQUESTED 결제를 영속(커밋)한다. 이 레코드가 있어야 호출 중 크래시에도 추적·정산이 가능하다.
 *
 * 주문을 비관 락으로 잡아 결제 대기 상태를 확인하고, 진행 중(REQUESTED/APPROVED) 결제가 없을 때만 생성한다 —
 * 주문 락 + dedupe 로 동시/중복 트리거 중 단 하나만 REQUESTED 를 만든다. (PaymentFacade 가 새 트랜잭션 경계로 호출)
 */
@Component
class PaymentInitiator(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun request(orderId: Long): Payment? {
        val order = orderRepository.findByIdForUpdate(orderId) ?: return null
        if (order.status != OrderStatus.PAYMENT_PENDING) return null
        val latest = paymentRepository.findLatestByOrderId(orderId)
        if (latest != null && (latest.isRequested() || latest.isApproved())) return null
        return paymentRepository.save(Payment.request(orderId = orderId, amount = order.totalAmount))
    }
}
