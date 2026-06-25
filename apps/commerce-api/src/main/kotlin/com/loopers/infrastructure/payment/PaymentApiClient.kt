package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransaction

/**
 * pg-simulator HTTP API 호출을 담당하는 전송(transport) 추상화.
 *
 * RestClient·Feign·WebClient 등 어떤 라이브러리를 쓰든 이 인터페이스 뒤로 감춰, 어댑터(`PgSimulatorPaymentGateway`)가
 * 전송 구현에 묶이지 않게 한다 — 라이브러리 교체는 구현체만 갈아끼우면 된다.
 *
 * 전송 실패(통신 실패·타임아웃·5xx)는 라이브러리 예외를 누수하지 않고 `PaymentGatewayException` 으로 변환해 던진다.
 * 회복 전략(타임아웃·회로 차단·재시도)은 본 계약의 책임이 아니라 어댑터가 이 호출을 감싸 적용한다.
 */
interface PaymentApiClient {
    fun requestPayment(command: PaymentRequestCommand): PaymentResponse

    fun getTransaction(userId: String, transactionKey: String): PgTransaction

    fun getByOrder(userId: String, orderId: Long): List<PgTransaction>
}
