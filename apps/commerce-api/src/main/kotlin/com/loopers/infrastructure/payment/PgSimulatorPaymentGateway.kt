package com.loopers.infrastructure.payment

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.loopers.application.payment.port.PaymentRequestResult
import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PgTransactionStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * pg-simulator 연동 어댑터. `request` 는 결과를 기다리지 않고 거래 식별자·처리 상태(접수)만 받는다.
 * 외부 호출은 트랜잭션·락 밖에서 일어나며, 응답 봉투(meta/data) 에서 결제 결과만 추출한다.
 */
@Component
class PgSimulatorPaymentGateway(
    private val pgRestClient: RestClient,
) : PaymentGateway {
    override fun request(request: PaymentRequestCommand): PaymentRequestResult {
        val response = pgRestClient.post()
            .uri("/api/v1/payments")
            .header(HEADER_USER_ID, request.userId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(PgPaymentRequest.from(request))
            .retrieve()
            .body(PgTransactionResponse::class.java)
        val data = requireNotNull(response?.data) { "PG 결제 요청 응답이 비어 있다." }
        return PaymentRequestResult(transactionKey = data.transactionKey, status = data.status)
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
