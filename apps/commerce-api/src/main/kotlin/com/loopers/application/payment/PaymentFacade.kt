package com.loopers.application.payment

import com.loopers.application.order.OrderCompensator
import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardNumber
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentErrorType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class PaymentFacade(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val orderCompensator: OrderCompensator,
) {
    @Transactional
    fun pay(command: PaymentCommand): PaymentRequestResult {
        // 카드 입력 검증(400) — 외부 호출·DB 조회 전에 먼저 거른다.
        val cardType = CardType.from(command.cardType)
        val cardNo = CardNumber(command.cardNo)

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
                cardType = cardType.name,
                cardNo = cardNo.value,
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

    /**
     * 정산 — 콜백·폴링이 전달한 외부 거래 결과를 결제·주문에 반영한다. 거래 식별자로 결제를 찾아 확정한다.
     * 성공이면 결제 APPROVED + 주문 PAID. (실패·미확정 분기는 후속 구현.)
     */
    @Transactional
    fun settle(transaction: PgTransaction) {
        // 거래 식별자로 결제를 비관 락 조회 — 콜백·폴링 동시 도착을 직렬화한다.
        val payment = paymentRepository.findByTransactionIdForUpdate(transaction.transactionKey) ?: return
        if (!payment.isRequested()) return // 이미 정산(승인·실패·취소)됨 — 중복 결과는 멱등하게 무시한다
        val order = orderRepository.findByIdForUpdate(payment.orderId) ?: return

        when (transaction.status) {
            PgTransactionStatus.SUCCESS -> {
                payment.approve(transaction.transactionKey, LocalDateTime.now())
                order.markPaid(transaction.transactionKey, transaction.status.name)
            }
            PgTransactionStatus.FAILED -> {
                payment.fail(transaction.reason)
                orderCompensator.restore(order)
                order.markPaymentFailed(transaction.transactionKey, transaction.reason)
            }
            PgTransactionStatus.PENDING -> return // 아직 처리 중 — 미확정으로 둔다(후속 item)
        }
        paymentRepository.save(payment)
        orderRepository.save(order)
    }

    /**
     * 수동 복구 — 처리 중(REQUESTED) 결제의 외부 상태를 거래 식별자로 조회해, 확정(SUCCESS/FAILED)이면 정산한다.
     * 이미 확정됐거나 거래 식별자 미확보이거나 외부가 아직 처리 중이면 상태를 바꾸지 않고 `settled = false` 로 반환한다.
     */
    @Transactional
    fun sync(paymentId: Long): PaymentSyncResult {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(PaymentErrorType.PAYMENT_NOT_FOUND)
        if (!payment.isRequested()) {
            return PaymentSyncResult(paymentId, payment.status, settled = false) // 이미 확정 — 변화 없음
        }
        // 거래 식별자 미확보(타임아웃 접수 미확인)는 이 경로로 확정할 수 없다 — 미확정으로 둔다.
        val transactionKey = payment.transactionId
            ?: return PaymentSyncResult(paymentId, payment.status, settled = false)
        val order = orderRepository.findById(payment.orderId)
            ?: throw CoreException(OrderErrorType.ORDER_NOT_FOUND)

        val transaction = paymentGateway.getTransaction(order.userId.toString(), transactionKey)
        if (transaction.status == PgTransactionStatus.PENDING) {
            return PaymentSyncResult(paymentId, payment.status, settled = false) // 외부 미확정
        }
        settle(transaction)
        val settled = paymentRepository.findById(paymentId)!!
        return PaymentSyncResult(paymentId, settled.status, settled = true)
    }

    companion object {
        // TODO: 환경별 외부화(payment.callback-url). pg-simulator 는 callbackUrl 이 http://localhost:8080 으로 시작할 것을 요구한다.
        private const val CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback"
    }
}
