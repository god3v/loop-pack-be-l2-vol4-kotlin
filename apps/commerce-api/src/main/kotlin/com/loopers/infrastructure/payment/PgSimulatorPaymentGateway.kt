package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentGatewayException
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransaction
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.stereotype.Component

/**
 * pg-simulator 연동 어댑터(`PaymentGateway` 포트 구현). 실제 HTTP 전송은 `PaymentApiClient` 에 위임하고,
 * 본 어댑터는 회복 전략(회로 차단기·재시도)과 포트 계약만 책임진다 — 전송 라이브러리(RestClient 등) 교체는 `PaymentApiClient` 구현만 갈아끼우면 된다.
 */
@Component
class PgSimulatorPaymentGateway(
    private val paymentApiClient: PaymentApiClient,
    private val pgCircuitBreaker: CircuitBreaker,
    private val pgRetry: Retry,
) : PaymentGateway {
    override fun request(request: PaymentRequestCommand): PaymentResponse =
        execute("결제 요청") { paymentApiClient.requestPayment(request) }

    override fun getTransaction(userId: String, transactionKey: String): PgTransaction =
        execute("결제 조회") { paymentApiClient.getTransaction(userId, transactionKey) }

    override fun getByOrder(userId: String, orderId: Long): List<PgTransaction> =
        execute("주문별 결제 조회") { paymentApiClient.getByOrder(userId, orderId) }

    /**
     * 전송 호출을 회로 차단기(안쪽)·재시도(바깥쪽)로 감싼다. 전송 실패는 `PaymentApiClient` 가 `PaymentGatewayException` 으로 변환해 던지며,
     * 각 시도가 회로 차단기를 거쳐 실패로 누적된다 — 누적 실패가 임계를 넘으면 회로가 열린다.
     * 회로가 열린 동안의 호출(`CallNotPermittedException`)은 재시도하지 않고 즉시 차단으로 변환한다.
     */
    private fun <T> execute(action: String, block: () -> T): T {
        val guarded = CircuitBreaker.decorateSupplier(pgCircuitBreaker) { block() }
        return try {
            Retry.decorateSupplier(pgRetry, guarded).get()
        } catch (e: CallNotPermittedException) {
            throw PaymentGatewayException("PG 회로 차단 — $action 차단됨", e)
        }
    }
}
