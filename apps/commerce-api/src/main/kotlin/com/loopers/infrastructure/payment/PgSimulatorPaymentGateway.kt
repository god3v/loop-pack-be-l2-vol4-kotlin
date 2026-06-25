package com.loopers.infrastructure.payment

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentGatewayException
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * pg-simulator 연동 어댑터. `request` 는 결과를 기다리지 않고 거래 식별자·처리 상태(접수)만 받는다.
 * 외부 호출은 트랜잭션·락 밖에서 일어나며, 응답 봉투(meta/data) 에서 결제 결과만 추출한다.
 */
@Component
class PgSimulatorPaymentGateway(
    private val pgRestClient: RestClient,
    private val pgCircuitBreaker: CircuitBreaker,
    private val pgRetry: Retry,
) : PaymentGateway {
    override fun request(request: PaymentRequestCommand): PaymentResponse = execute("결제 요청") {
        val response = pgRestClient.post()
            .uri("/api/v1/payments")
            .header(HEADER_USER_ID, request.userId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(PgPaymentRequest.from(request))
            .retrieve()
            .body(PgTransactionResponse::class.java)
        val data = requireNotNull(response?.data) { "PG 결제 요청 응답이 비어 있다." }
        PaymentResponse(transactionKey = data.transactionKey, status = data.status)
    }

    override fun getTransaction(userId: String, transactionKey: String): PgTransaction = execute("결제 조회") {
        val response = pgRestClient.get()
            .uri("/api/v1/payments/{transactionKey}", transactionKey)
            .header(HEADER_USER_ID, userId)
            .retrieve()
            .body(PgTransactionResponse::class.java)
        val data = requireNotNull(response?.data) { "PG 결제 조회 응답이 비어 있다." }
        PgTransaction(transactionKey = data.transactionKey, status = data.status, reason = data.reason)
    }

    override fun getByOrder(userId: String, orderId: Long): List<PgTransaction> = execute("주문별 결제 조회") {
        val response = pgRestClient.get()
            .uri { it.path("/api/v1/payments").queryParam("orderId", orderId).build() }
            .header(HEADER_USER_ID, userId)
            .retrieve()
            .body(PgOrderResponse::class.java)
        val transactions = response?.data?.transactions ?: emptyList()
        transactions.map { PgTransaction(transactionKey = it.transactionKey, status = it.status, reason = it.reason) }
    }

    /**
     * 외부 호출을 회로 차단기(안쪽)·재시도(바깥쪽)로 감싸고, 인프라 예외(통신 실패·타임아웃·5xx)·회로 차단을 포트 예외로 변환한다.
     * 일시 장애로 변환된 포트 예외는 재시도 대상이며, 각 시도가 회로 차단기를 거쳐 실패로 누적된다 — 누적 실패가 임계를 넘으면 회로가 열린다.
     * 회로가 열린 동안의 호출(`CallNotPermittedException`)은 재시도하지 않고 즉시 차단으로 변환한다.
     */
    private fun <T> execute(action: String, block: () -> T): T {
        val guarded = CircuitBreaker.decorateSupplier(pgCircuitBreaker) {
            try {
                block()
            } catch (e: RestClientException) {
                throw PaymentGatewayException("PG $action 실패", e)
            }
        }
        return try {
            Retry.decorateSupplier(pgRetry, guarded).get()
        } catch (e: CallNotPermittedException) {
            throw PaymentGatewayException("PG 회로 차단 — $action 차단됨", e)
        }
    }

    companion object {
        private const val HEADER_USER_ID = "X-USER-ID"
    }
}

internal data class PgPaymentRequest(
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
) {
    companion object {
        fun from(request: PaymentRequestCommand): PgPaymentRequest = PgPaymentRequest(
            orderId = request.orderId.toString(),
            cardType = request.cardType,
            cardNo = request.cardNo,
            amount = request.amount,
            callbackUrl = request.callbackUrl,
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PgTransactionResponse(
    val data: Data?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Data(
        val transactionKey: String,
        val status: PgTransactionStatus,
        val reason: String?,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PgOrderResponse(
    val data: Data?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Data(
        val transactions: List<Transaction> = emptyList(),
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Transaction(
            val transactionKey: String,
            val status: PgTransactionStatus,
            val reason: String?,
        )
    }
}
