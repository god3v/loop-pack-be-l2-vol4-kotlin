package com.loopers.infrastructure.payment

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.loopers.application.payment.port.PaymentGatewayException
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * `PaymentApiClient` 의 RestClient 구현. pg-simulator 응답 봉투(meta/data) 에서 결제 결과만 추출하고,
 * RestClient 예외(통신 실패·타임아웃·5xx)를 `PaymentGatewayException` 으로 변환해 전송 라이브러리 예외가 상위로 누수되지 않게 한다.
 * 전송 라이브러리를 바꾸려면 본 구현 대신 다른 `PaymentApiClient` 구현을 등록하면 된다.
 */
@Component
class RestClientPaymentApiClient(
    private val pgRestClient: RestClient,
) : PaymentApiClient {
    override fun requestPayment(command: PaymentRequestCommand): PaymentResponse = call("결제 요청") {
        val response = pgRestClient.post()
            .uri("/api/v1/payments")
            .header(HEADER_USER_ID, command.userId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(PgPaymentRequest.from(command))
            .retrieve()
            .body(PgTransactionResponse::class.java)
        val data = requireNotNull(response?.data) { "PG 결제 요청 응답이 비어 있다." }
        PaymentResponse(transactionKey = data.transactionKey, status = data.status)
    }

    override fun getTransaction(userId: String, transactionKey: String): PgTransaction = call("결제 조회") {
        val response = pgRestClient.get()
            .uri("/api/v1/payments/{transactionKey}", transactionKey)
            .header(HEADER_USER_ID, userId)
            .retrieve()
            .body(PgTransactionResponse::class.java)
        val data = requireNotNull(response?.data) { "PG 결제 조회 응답이 비어 있다." }
        PgTransaction(transactionKey = data.transactionKey, status = data.status, reason = data.reason)
    }

    override fun getByOrder(userId: String, orderId: Long): List<PgTransaction> = call("주문별 결제 조회") {
        val response = pgRestClient.get()
            .uri { it.path("/api/v1/payments").queryParam("orderId", orderId).build() }
            .header(HEADER_USER_ID, userId)
            .retrieve()
            .body(PgOrderResponse::class.java)
        val transactions = response?.data?.transactions ?: emptyList()
        transactions.map { PgTransaction(transactionKey = it.transactionKey, status = it.status, reason = it.reason) }
    }

    /**
     * RestClient 예외를 포트 예외로 변환한다.
     * - **5xx·타임아웃·통신 실패**(`RestClientException`) → `PaymentGatewayException`: 일시 장애. 재시도·회로 차단 대상.
     * - **4xx**(`HttpClientErrorException`) → **감싸지 않고 그대로 전파**: 우리 요청이 PG 규약에 안 맞는 결정적 실패라, 재시도(`retryExceptions` 화이트리스트)·회로 차단(`recordExceptions` 화이트리스트)에서 자동 제외되고 Facade Fallback(=`PaymentGatewayException` 한정)에도 안 잡혀 빠르게 실패한다. (전송 예외 비누수 원칙의 의도적·좁은 예외 — ADR-003.)
     */
    private fun <T> call(action: String, block: () -> T): T =
        try {
            block()
        } catch (e: HttpClientErrorException) {
            throw e
        } catch (e: RestClientException) {
            throw PaymentGatewayException("PG $action 실패", e)
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
        fun from(command: PaymentRequestCommand): PgPaymentRequest = PgPaymentRequest(
            orderId = command.orderId.toString(),
            cardType = command.cardType,
            cardNo = command.cardNo,
            amount = command.amount,
            callbackUrl = command.callbackUrl,
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
