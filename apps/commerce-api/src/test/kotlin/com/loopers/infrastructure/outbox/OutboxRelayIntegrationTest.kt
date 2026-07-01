package com.loopers.infrastructure.outbox

import com.loopers.application.order.OrderFacade
import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.CompletableFuture

/**
 * Outbox 가 유실 창을 어떻게 메우는지 드러낸다 — 발행이 실패해도 이벤트는 커밋된 아웃박스에 남고,
 * 브로커 복구 후 릴레이 재시도로 결국 발행된다(At Least Once).
 *
 * Outbox 가 없었다면: 주문 커밋 직후 직접 Kafka 발행이 실패하는 순간 그 이벤트는 소실된다(복구 근거 없음).
 * Outbox 가 있으면: 발행은 같은 트랜잭션에 적재된 행에 대한 별도 재시도이므로, 발행 실패는 유실이 아니라 지연일 뿐이다.
 */
@SpringBootTest
class OutboxRelayIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val relay: OutboxRelay,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    // relaxUnitFun: 실제 KafkaTemplate 은 SmartInitializingSingleton 이라 기동 시 afterSingletonsInstantiated()(void) 를 호출한다.
    // void 만 relaxed 로 풀어 컨텍스트를 띄우고, send() 는 strict 로 둬 테스트가 발행을 명시 제어한다.
    @MockkBean(relaxUnitFun = true)
    private lateinit var kafkaTemplate: KafkaTemplate<Any, Any>

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `발행이 실패해도 이벤트는 outbox 에 남고, 브로커 복구 후 릴레이 재시도로 발행된다`() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(price = 1000, stock = 10))
        orderFacade.placeOrder(
            PlaceOrderCommand(
                loginId = user.loginId,
                idempotencyKey = "relay-gap-1",
                lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
            ),
        )

        // 브로커 다운 — 발행 실패
        every { kafkaTemplate.send(any<String>(), any(), any()) } throws RuntimeException("broker down")
        relay.relay()

        // 유실 없음: 이벤트는 여전히 PENDING 으로 outbox 에 남아 재시도 근거가 된다
        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING)).hasSize(1)
        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PUBLISHED)).isEmpty()

        // 브로커 복구 — 다음 릴레이 주기
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns CompletableFuture.completedFuture(mockk())
        relay.relay()

        // 재시도로 결국 발행됨
        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING)).isEmpty()
        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PUBLISHED)).hasSize(1)
        verify { kafkaTemplate.send("order-events", any(), any()) }
    }
}
