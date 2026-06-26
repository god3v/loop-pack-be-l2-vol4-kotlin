package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentGatewayException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class PgPaymentClientConfig {
    /**
     * 외부 PG 연동 회로 차단기 — 반복 실패·느린 응답이 임계치를 넘으면 회로를 열어 이후 호출을 외부까지 가지 않고 즉시 차단한다.
     * 차단 중 호출은 `CallNotPermittedException` 으로 거부되며, 어댑터가 이를 `PaymentGatewayException` 으로 변환해 Fallback 으로 흐른다.
     *
     * **재시도를 바깥에서 감싼다**(어댑터 `execute`: `CircuitBreaker(Retry(call))`) — 한 요청의 재시도 묶음이 차단기에 한 이벤트로 집계되고,
     * half-open permit 을 1요청당 1개만 소비한다(가벼운 probe). 배경은 `docs/domain/payment/resilience-decisions.md` ADR-001.
     *
     * 임계치 튜닝 근거는 ADR-002:
     * - `slowCallDurationThreshold(1s)` — read timeout(2s)보다 **작게** 둬, 타임아웃에 끊기기 전 "느린 호출"이 실제로 집계되게 한다(겹치면 사문화).
     * - `slowCallRateThreshold(50%)` — 느린 호출이 절반이면 차단(failure-rate 와 정렬). 100% 면 거의 발동하지 않는다.
     * - `recordExceptions(PaymentGatewayException)` — PG 전송 실패(타임아웃·통신·5xx)만 차단에 집계. 우리 측 예외(응답 파싱 오류·버그)는 회로를 열지 않는다.
     * - `automaticTransition(true)` — 대기 경과 시 호출 없이도 HALF_OPEN 으로 자동 전이(기본 공유 스케줄러). call-driven 이라 probe 처리 결과는 동치이나 전이 시점이 정확해진다.
     * - window(10)·permitted(3) 는 현행 유지 — 사유·재검토 시점은 ADR-002.
     */
    @Bean
    fun pgCircuitBreaker(): CircuitBreaker {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50f)
            // read timeout(2s)보다 작게 — 타임아웃에 끊기기 전의 "느린 성공"이 slow-call 로 집계되게 한다.
            .slowCallDurationThreshold(Duration.ofSeconds(1))
            .slowCallRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            // half-open 은 가볍게 — 재시도가 안쪽이라 permit 1개 = 사용자 요청 1번(시도 묶음 전체).
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            // PG 전송 실패만 차단에 집계 — 그 외 예외(4xx HttpClientErrorException, 우리 측 파싱 오류·버그)는 회로를 열지 않는다.
            .recordExceptions(PaymentGatewayException::class.java)
            .build()
        return CircuitBreaker.of("pg", config)
    }

    /**
     * 외부 PG 연동 재시도 — 일시 장애(통신 실패·타임아웃·5xx)로 변환된 `PaymentGatewayException` 만 재시도한다.
     * 영구 실패(잘못된 카드·한도 초과)는 비동기 콜백으로 도착하므로 이 경로를 타지 않는다.
     *
     * **4xx(`HttpClientErrorException`)는 재시도 제외** (ADR-003) — 우리 요청이 PG 규약에 안 맞는 결정적 실패라 재시도해도 동일하다.
     * `retryExceptions` 화이트리스트가 이미 제외하지만, `ignoreExceptions` 로 의도를 명시한다(재시도 정책이 4xx 제외를 직접 소유).
     *
     * **지수 + 지터(random) backoff** (ADR-003) — 고정 간격은 동시 다발 재시도가 회복 중인 PG 를 같은 박자로 때리는 thundering herd 를 부른다.
     * 지터로 재시도 시점을 흩고, 지수로 실패가 이어질수록 간격을 늘린다. 시도 간 대기 ≈ 200ms → 400ms 에 ±50% 지터(총 backoff ≈ 0.3~0.9s).
     */
    @Bean
    fun pgRetry(): Retry {
        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(200), 2.0, 0.5))
            .retryExceptions(PaymentGatewayException::class.java)
            .ignoreExceptions(HttpClientErrorException::class.java)
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
