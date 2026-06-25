package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentGatewayException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class PgPaymentClientConfig {
    /**
     * 외부 PG 연동 회로 차단기 — 반복 실패·느린 응답이 임계치를 넘으면 회로를 열어 이후 호출을 외부까지 가지 않고 즉시 차단한다.
     * 차단 중 호출은 `CallNotPermittedException` 으로 거부되며, 어댑터가 이를 `PaymentGatewayException` 으로 변환해 Fallback 으로 흐른다.
     */
    @Bean
    fun pgCircuitBreaker(): CircuitBreaker {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50f)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .slowCallRateThreshold(100f)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .build()
        return CircuitBreaker.of("pg", config)
    }

    /**
     * 외부 PG 연동 재시도 — 일시 장애(통신 실패·타임아웃·5xx)로 변환된 `PaymentGatewayException` 만 backoff 를 두고 재시도한다.
     * 영구 실패(잘못된 카드·한도 초과)는 비동기 콜백으로 도착하므로 이 경로를 타지 않고, 회로 차단(`CallNotPermittedException`)은 재시도 대상이 아니다.
     */
    @Bean
    fun pgRetry(): Retry {
        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(200))
            .retryExceptions(PaymentGatewayException::class.java)
            .build()
        return Retry.of("pg", config)
    }

    /**
     * pg-simulator 연동 RestClient. 연결·읽기 타임아웃을 둬, 외부가 지연·무응답이어도 스레드를 무한 점유하지 않는다.
     * 타임아웃 시 발생하는 `ResourceAccessException` 은 어댑터에서 `PaymentGatewayException` 으로 변환된다.
     */
    @Bean
    fun pgRestClient(
        builder: RestClient.Builder,
        @Value("\${pg-simulator.base-url:http://localhost:8082}") baseUrl: String,
        @Value("\${pg-simulator.connect-timeout-ms:1000}") connectTimeoutMs: Long,
        @Value("\${pg-simulator.read-timeout-ms:2000}") readTimeoutMs: Long,
    ): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            setReadTimeout(Duration.ofMillis(readTimeoutMs))
        }
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build()
    }
}
