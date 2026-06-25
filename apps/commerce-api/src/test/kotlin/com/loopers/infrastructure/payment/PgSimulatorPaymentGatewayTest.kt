package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentGatewayException
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PgTransactionStatus
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Duration

class PgSimulatorPaymentGatewayTest {
    private fun cb(config: CircuitBreakerConfig = CircuitBreakerConfig.ofDefaults()): CircuitBreaker =
        CircuitBreaker.of("test", config)

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
        val gateway = PgSimulatorPaymentGateway(builder.build(), cb())

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
        val gateway = PgSimulatorPaymentGateway(builder.build(), cb())

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
        val gateway = PgSimulatorPaymentGateway(builder.build(), cb())

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

    @DisplayName("외부가 5xx 로 실패하면 RestClient 예외가 누수되지 않고 PaymentGatewayException 으로 변환된다.")
    @Test
    fun translatesServerErrorToGatewayException() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(builder.build(), cb())

        server.expect(requestTo("http://pg.test/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError())

        assertThatThrownBy { gateway.request(request()) }
            .isInstanceOf(PaymentGatewayException::class.java)
        server.verify()
    }

    @DisplayName("외부가 반복 실패하면 회로가 열려, 이후 호출은 외부를 거치지 않고 PaymentGatewayException 으로 차단된다.")
    @Test
    fun opensCircuitAfterRepeatedFailures() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val circuit = cb(
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build(),
        )
        val gateway = PgSimulatorPaymentGateway(builder.build(), circuit)

        // 외부는 딱 2번만 실패 응답한다 — 회로가 열리면 3번째 호출은 외부로 가지 않는다.
        repeat(2) {
            server.expect(requestTo("http://pg.test/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError())
        }

        repeat(2) {
            assertThatThrownBy { gateway.request(request()) }.isInstanceOf(PaymentGatewayException::class.java)
        }
        // 회로 OPEN — 외부를 거치지 않고 즉시 차단된다.
        assertThatThrownBy { gateway.request(request()) }.isInstanceOf(PaymentGatewayException::class.java)

        assertThat(circuit.state).isEqualTo(CircuitBreaker.State.OPEN)
        server.verify() // 외부 호출은 정확히 2번만 일어났다(3번째는 차단)
    }
}
