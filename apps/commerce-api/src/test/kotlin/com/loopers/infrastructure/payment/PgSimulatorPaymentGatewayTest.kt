package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PgTransactionStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class PgSimulatorPaymentGatewayTest {
    private fun request() = PaymentRequestCommand(
        userId = "7",
        orderId = 1000001L,
        amount = 31500L,
        cardType = "SAMSUNG",
        cardNo = "1234-5678-9814-1451",
        callbackUrl = "http://localhost:8080/api/v1/payments/callback",
    )

    @DisplayName("request 는 PG 에 결제를 요청하고 발급된 거래 식별자와 처리 중(PENDING) 상태를 받는다.")
    @Test
    fun requestReturnsTransactionKeyAndPending() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(builder.build())

        server.expect(requestTo("http://pg.test/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-USER-ID", "7"))
            .andExpect(jsonPath("$.orderId").value("1000001"))
            .andExpect(jsonPath("$.cardType").value("SAMSUNG"))
            .andExpect(jsonPath("$.amount").value(31500))
            .andExpect(jsonPath("$.callbackUrl").value("http://localhost:8080/api/v1/payments/callback"))
            .andRespond(
                withSuccess(
                    """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},""" +
                        """"data":{"transactionKey":"20260623:TR:9577c5","status":"PENDING","reason":null}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway.request(request())

        assertThat(result.transactionKey).isEqualTo("20260623:TR:9577c5")
        assertThat(result.status).isEqualTo(PgTransactionStatus.PENDING)
        server.verify()
    }

    @DisplayName("getTransaction 은 거래 식별자로 외부 결제 상태(성공/실패/처리중)를 조회한다.")
    @Test
    fun getTransactionReturnsStatus() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(builder.build())

        // RestClient 가 path 변수의 콜론을 %3A 로 인코딩한다(서버는 디코딩해 매칭). 실제 송신 URI 를 검증한다.
        server.expect(requestTo("http://pg.test/api/v1/payments/20260623%3ATR%3A9577c5"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "7"))
            .andRespond(
                withSuccess(
                    """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},""" +
                        """"data":{"transactionKey":"20260623:TR:9577c5","orderId":"1000001","cardType":"SAMSUNG",""" +
                        """"cardNo":"1234-5678-9814-1451","amount":31500,"status":"SUCCESS","reason":null}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val tx = gateway.getTransaction(userId = "7", transactionKey = "20260623:TR:9577c5")

        assertThat(tx.transactionKey).isEqualTo("20260623:TR:9577c5")
        assertThat(tx.status).isEqualTo(PgTransactionStatus.SUCCESS)
        server.verify()
    }

    @DisplayName("getByOrder 는 주문 식별자로 외부 결제건들을 조회한다 (거래 식별자 미확보 복구).")
    @Test
    fun getByOrderReturnsTransactions() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(builder.build())

        server.expect(requestTo("http://pg.test/api/v1/payments?orderId=1000001"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "7"))
            .andRespond(
                withSuccess(
                    """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},""" +
                        """"data":{"orderId":"1000001","transactions":[""" +
                        """{"transactionKey":"20260623:TR:9577c5","status":"SUCCESS","reason":null}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val txs = gateway.getByOrder(userId = "7", orderId = 1000001L)

        assertThat(txs).hasSize(1)
        assertThat(txs.single().transactionKey).isEqualTo("20260623:TR:9577c5")
        assertThat(txs.single().status).isEqualTo(PgTransactionStatus.SUCCESS)
        server.verify()
    }
}
