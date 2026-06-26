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
     * 전송 호출을 재시도(안쪽)·회로 차단기(바깥쪽)로 감싼다. 전송 실패는 `PaymentApiClient` 가 `PaymentGatewayException` 으로 변환해 던진다.
     * 한 번의 호출에서 **재시도 시퀀스 전체가 회로 차단기에 한 번의 이벤트로 집계**된다 — half-open 에서 한 요청이 permit 하나만 쓰도록(가벼운 probe) 회로 차단기를 바깥에 둔다.
     * 회로가 열린 동안의 호출은 재시도에 들어가기 *전에* `CallNotPermittedException` 으로 차단되어, 죽어 있을 수 있는 PG 로 재시도가 새어 나가지 않는다.
     */
    private fun <T> execute(action: String, block: () -> T): T {
        val retried = Retry.decorateSupplier(pgRetry) { block() }
        val guarded = CircuitBreaker.decorateSupplier(pgCircuitBreaker, retried)
        return try {
            guarded.get()
        } catch (e: CallNotPermittedException) {
            throw PaymentGatewayException("PG 회로 차단 — $action 차단됨", e)
        }
    }
}
