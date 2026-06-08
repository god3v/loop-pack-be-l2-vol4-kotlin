package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderRepository
import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderStatus
import com.loopers.support.error.CoreException
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, OrderRepositoryImpl::class)
class OrderRepositoryImplIntegrationTest @Autowired constructor(
    private val orderRepository: OrderRepository,
    private val testEntityManager: TestEntityManager,
) {
    private fun line(productId: Long = 1L, price: Int = 1000, qty: Int = 1) =
        OrderLine.create(productId = productId, productName = "P-$productId", unitPrice = price, quantity = qty)

    private fun persistPaid(
        userId: Long = 1L,
        idempotencyKey: String = "k-${System.nanoTime()}",
        lines: List<OrderLine> = listOf(line()),
    ): Order {
        val order = Order.create(userId = userId, lines = lines, idempotencyKey = idempotencyKey)
            .also { it.markPaid("tx-1", "APPROVED") }
        val saved = orderRepository.save(order)
        testEntityManager.flush()
        return saved
    }

    @DisplayName("save / findById 라운드트립")
    @Nested
    inner class SaveAndFind {
        @DisplayName("save 후 findById 로 동일 도메인 객체가 복원된다 (라인 스냅샷·status·운영 메타 포함).")
        @Test
        fun roundTrip() {
            val saved = persistPaid(
                userId = 7L,
                lines = listOf(
                    line(productId = 1L, price = 1000, qty = 2),
                    line(productId = 2L, price = 2000, qty = 3),
                ),
            )
            testEntityManager.clear()

            val found = orderRepository.findById(saved.id)

            assertThat(found).isNotNull()
            assertThat(found!!.status).isEqualTo(OrderStatus.PAID)
            assertThat(found.paymentTransactionId).isEqualTo("tx-1")
            assertThat(found.paymentResultCode).isEqualTo("APPROVED")
            assertThat(found.totalAmount).isEqualTo(1000 * 2 + 2000 * 3)
            assertThat(found.lines).hasSize(2)
            assertThat(found.lines.map { it.productName }).containsExactlyInAnyOrder("P-1", "P-2")
        }

        @DisplayName("DB 에 존재하지 않는 id 로 save(update) 하면, ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsOrderNotFound_whenUpdatingNonExistentId() {
            val ghost = Order(
                id = 999L,
                userId = 1L,
                lines = listOf(line()),
                orderedAt = LocalDateTime.now(),
                idempotencyKey = "ghost",
            )

            val ex = assertThrows<CoreException> { orderRepository.save(ghost) }

            assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_NOT_FOUND)
        }
    }

    @DisplayName("findByUserIdAndIdempotencyKey")
    @Nested
    inner class IdempotencyLookup {
        @DisplayName("정확히 판정한다.")
        @Test
        fun findsByCompositeKey() {
            val saved = persistPaid(userId = 7L, idempotencyKey = "idem-x")
            testEntityManager.clear()

            assertThat(orderRepository.findByUserIdAndIdempotencyKey(7L, "idem-x")?.id).isEqualTo(saved.id)
            assertThat(orderRepository.findByUserIdAndIdempotencyKey(7L, "other")).isNull()
            assertThat(orderRepository.findByUserIdAndIdempotencyKey(99L, "idem-x")).isNull()
        }
    }

    @DisplayName("findAllByUserIdAndOrderedAtBetween")
    @Nested
    inner class MyListing {
        @DisplayName("orderedAt desc 정렬 + 기간 필터 + 본인 userId 필터가 모두 적용된다.")
        @Test
        fun filtersAndOrders() {
            val a = persistPaid(userId = 7L)
            Thread.sleep(10)
            val b = persistPaid(userId = 7L)
            persistPaid(userId = 99L)
            testEntityManager.clear()

            val result = orderRepository.findAllByUserIdAndOrderedAtBetween(
                userId = 7L,
                start = LocalDateTime.now().minusDays(1),
                end = LocalDateTime.now().plusDays(1),
                page = 0,
                size = 10,
            )

            assertThat(result.map { it.id }).containsExactly(b.id, a.id)
        }
    }

    @DisplayName("findAllForAdmin")
    @Nested
    inner class AdminListing {
        @DisplayName("orderedAt desc 정렬 + 페이징이 적용된다 (전 회원 포함).")
        @Test
        fun adminOrdersAndPages() {
            val a = persistPaid(userId = 7L)
            Thread.sleep(10)
            val b = persistPaid(userId = 8L)
            Thread.sleep(10)
            val c = persistPaid(userId = 9L)
            testEntityManager.clear()

            val page0 = orderRepository.findAllForAdmin(0, 2)
            val page1 = orderRepository.findAllForAdmin(1, 2)

            assertThat(page0.map { it.id }).containsExactly(c.id, b.id)
            assertThat(page1.map { it.id }).containsExactly(a.id)
        }
    }
}
