package com.loopers.infrastructure.outbox

import com.loopers.application.order.OrderFacade
import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Transactional Outbox 기록 — 주문 커밋 시 `OrderEvent.Created` 가 같은 트랜잭션에서 `outbox` 행으로 적재되는지 검증한다.
 * (Kafka 발행은 릴레이 증분에서, 여기서는 dual-write 를 없앤 "DB write + outbox 기록 원자성" 만 확인)
 */
@SpringBootTest
class OrderOutboxIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `주문이 커밋되면 outbox 에 ORDER aggregate 행이 PENDING 으로 같은 트랜잭션에 기록된다`() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(price = 1000, stock = 10))

        val result = orderFacade.placeOrder(
            PlaceOrderCommand(
                loginId = user.loginId,
                idempotencyKey = "outbox-order-1",
                lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
            ),
        )

        val rows = outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].aggregateType).isEqualTo("ORDER")
        assertThat(rows[0].aggregateId).isEqualTo(result.orderId.toString())
        assertThat(rows[0].eventType).isEqualTo("ORDER_CREATED")
        assertThat(rows[0].payload).contains("\"userId\":${result.userId}")
        assertThat(rows[0].status).isEqualTo(OutboxStatus.PENDING)
        assertThat(rows[0].occurredAt).isNotNull()
        assertThat(rows[0].publishedAt).isNull()
    }
}
