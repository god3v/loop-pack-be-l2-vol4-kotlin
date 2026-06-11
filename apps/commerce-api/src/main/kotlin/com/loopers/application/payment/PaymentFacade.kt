package com.loopers.application.payment

import com.loopers.application.order.port.PaymentGateway
import com.loopers.domain.payment.PaymentErrorType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component

/**
 * 주문 결제 오케스트레이션. 주문 저장 트랜잭션 커밋 후(이벤트 리스너가 트리거) 실행된다.
 *
 * 트랜잭션 경계를 두지 않는다 — 외부 결제 호출(charge·refund)을 어떤 락/트랜잭션에도 가두지 않기 위함이다.
 * 결제: 요청(REQUESTED 커밋) → **락 밖** charge → 정산. 취소: **락 밖** refund(승인분) → 취소 정산.
 * 각 트랜잭션 경계는 별도 빈(`PaymentInitiator`·`PaymentSettler`·`PaymentCanceler`)이 소유한다(REQUIRES_NEW — 프록시 경유 필수).
 */
@Component
class PaymentFacade(
    private val paymentInitiator: PaymentInitiator,
    private val paymentSettler: PaymentSettler,
    private val paymentCanceler: PaymentCanceler,
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
) {
    fun pay(orderId: Long) {
        // 요청이 성사되지 않으면(이미 처리·진행 중·주문 없음) 외부 호출 없이 종료 — 중복 트리거 방어.
        val payment = paymentInitiator.request(orderId) ?: return
        val result = paymentGateway.charge(payment.orderId, payment.amount)
        paymentSettler.settle(payment.id, result)
    }

    fun cancel(paymentId: Long) {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(PaymentErrorType.PAYMENT_NOT_FOUND)
        // 승인된 결제만 외부 환불이 필요하다(REQUESTED 는 청구 전이라 환불 불필요). 환불은 락 밖에서 호출한다.
        if (payment.isApproved()) {
            paymentGateway.refund(requireNotNull(payment.transactionId), payment.amount)
        }
        paymentCanceler.cancel(paymentId)
    }
}
