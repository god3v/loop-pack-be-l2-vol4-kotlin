package com.loopers.application.order

import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserRepository
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("OrderFacade")
class OrderFacadeTest {
    private val userRepository: UserRepository = mockk()
    private val productRepository: ProductRepository = mockk()
    private val orderRepository: OrderRepository = mockk()
    private val orderService = OrderService()
    private val orderFacade = OrderFacade(userRepository, productRepository, orderRepository, orderService)

    private val loginId = UserFixture.DEFAULT_LOGIN_ID
    private val idempotencyKey = "idem-001"

    private fun placeOrderCommand(
        couponId: Long? = null,
        lines: List<OrderLineCommand> = listOf(OrderLineCommand(productId = 1L, quantity = 1)),
    ) = PlaceOrderCommand(
        loginId = loginId,
        idempotencyKey = idempotencyKey,
        couponId = couponId,
        lines = lines,
    )

    @Nested
    @DisplayName("placeOrder — UC-1 정상 흐름")
    inner class PlaceOrderHappy {
        @Test
        @DisplayName("각 상품의 deductStock 이 호출되고 Order 가 저장된다")
        fun savesOrder() {
            val user = UserFixture.validUser()
            val productA = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            val productB = ProductFixture.validProduct(id = 2L, name = "B", price = 2000, stock = 5)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIds(any()) } returns listOf(productA, productB)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(
                    lines = listOf(
                        OrderLineCommand(productId = 1L, quantity = 2),
                        OrderLineCommand(productId = 2L, quantity = 3),
                    ),
                ),
            )

            assertThat(productA.stock.value).isEqualTo(8)
            assertThat(productB.stock.value).isEqualTo(2)
            verify(exactly = 1) { productRepository.findAllByIds(any()) }
            verify(exactly = 1) { productRepository.saveAll(any()) }
        }

        @Test
        @DisplayName("저장된 주문의 라인 스냅샷에는 호출 시점 상품의 name · price 가 박혀 있다")
        fun linesCarrySnapshot() {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "맥북", price = 1_000_000, stock = 10)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIds(any()) } returns listOf(product)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(lines = listOf(OrderLineCommand(productId = 1L, quantity = 2))),
            )

            assertThat(savedOrder.captured.lines[0].productName).isEqualTo("맥북")
            assertThat(savedOrder.captured.lines[0].unitPrice).isEqualTo(1_000_000)
        }

        @Test
        @DisplayName("totalAmount 는 unitPrice × quantity 합과 같다")
        fun totalAmountIsSumOfSubtotals() {
            val user = UserFixture.validUser()
            val productA = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            val productB = ProductFixture.validProduct(id = 2L, name = "B", price = 2000, stock = 5)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIds(any()) } returns listOf(productA, productB)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(
                    lines = listOf(
                        OrderLineCommand(productId = 1L, quantity = 2),
                        OrderLineCommand(productId = 2L, quantity = 3),
                    ),
                ),
            )

            assertThat(savedOrder.captured.totalAmount).isEqualTo(1000 * 2 + 2000 * 3)
        }

        @Test
        @DisplayName("couponId 가 있어도 합계는 변하지 않는다 (입력 슬롯만)")
        fun couponDoesNotAffectTotal() {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIds(any()) } returns listOf(product)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(
                    couponId = 99L,
                    lines = listOf(OrderLineCommand(productId = 1L, quantity = 2)),
                ),
            )

            assertThat(savedOrder.captured.totalAmount).isEqualTo(2000)
        }
    }

    @Nested
    @DisplayName("placeOrder — UC-1 멱등 수렴")
    inner class Idempotency {
        @Test
        @DisplayName("같은 (userId, idempotencyKey) 재호출 시 신규 저장 없이 기존 OrderResult 가 반환된다")
        fun returnsExistingOrder() {
            val user = UserFixture.validUser()
            val existing = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "X", 100, 1)),
                idempotencyKey = idempotencyKey,
            ).also { it.markPaid("prev-tx", "APPROVED") }
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns existing

            val result = orderFacade.placeOrder(placeOrderCommand())

            assertThat(result.status).isEqualTo(OrderStatus.PAID)
            verify(exactly = 0) { productRepository.findById(any()) }
            verify(exactly = 0) { orderRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("placeOrder — UC-1 예외 흐름")
    inner class PlaceOrderExceptions {
        @Test
        @DisplayName("loginId 회원 없음 → UNAUTHORIZED")
        fun unauthorizedWhenUserMissing() {
            every { userRepository.findByLoginId(loginId) } returns null

            val ex = assertThrows<CoreException> { orderFacade.placeOrder(placeOrderCommand()) }
            assertThat(ex.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        @DisplayName("라인 중 상품 없음 → PRODUCT_NOT_FOUND, orderRepository.save 미호출")
        fun productNotFound() {
            val user = UserFixture.validUser()
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIds(any()) } returns emptyList()

            val ex = assertThrows<CoreException> {
                orderFacade.placeOrder(
                    placeOrderCommand(lines = listOf(OrderLineCommand(productId = 99L, quantity = 1))),
                )
            }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
            verify(exactly = 0) { orderRepository.save(any()) }
            verify(exactly = 0) { productRepository.saveAll(any()) }
        }

        @Test
        @DisplayName("재고 부족 → INSUFFICIENT_STOCK, orderRepository.save 미호출")
        fun insufficientStock() {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 1)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIds(any()) } returns listOf(product)

            val ex = assertThrows<CoreException> {
                orderFacade.placeOrder(
                    placeOrderCommand(lines = listOf(OrderLineCommand(productId = 1L, quantity = 5))),
                )
            }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.INSUFFICIENT_STOCK)
            verify(exactly = 0) { orderRepository.save(any()) }
            verify(exactly = 0) { productRepository.saveAll(any()) }
        }

        @Test
        @DisplayName("idempotencyKey 가 blank 면 IDEMPOTENCY_KEY_BLANK 예외 (도메인 위임)")
        fun idempotencyKeyBlank() {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, "") } returns null
            every { productRepository.findAllByIds(any()) } returns listOf(product)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }

            val ex = assertThrows<CoreException> {
                orderFacade.placeOrder(
                    PlaceOrderCommand(
                        loginId = loginId,
                        idempotencyKey = "",
                        couponId = null,
                        lines = listOf(OrderLineCommand(productId = 1L, quantity = 1)),
                    ),
                )
            }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.IDEMPOTENCY_KEY_BLANK)
        }
    }

    @Nested
    @DisplayName("getMyOrders — UC-2")
    inner class GetMyOrders {
        @Test
        @DisplayName("기간 + 페이징 + 본인 userId 가 Repository 에 전달된다")
        fun delegatesFiltering() {
            val user = UserFixture.validUser()
            val start = LocalDateTime.of(2026, 5, 1, 0, 0)
            val end = LocalDateTime.of(2026, 5, 28, 23, 59)
            every { userRepository.findByLoginId(loginId) } returns user
            every {
                orderRepository.findAllByUserIdAndOrderedAtBetween(user.id, start, end, 1, 30)
            } returns emptyList()

            orderFacade.getMyOrders(loginId, start, end, page = 1, size = 30)

            verify { orderRepository.findAllByUserIdAndOrderedAtBetween(user.id, start, end, 1, 30) }
        }

        @Test
        @DisplayName("loginId 회원 없음 → UNAUTHORIZED")
        fun unauthorized() {
            every { userRepository.findByLoginId(loginId) } returns null

            val ex = assertThrows<CoreException> {
                orderFacade.getMyOrders(loginId, null, null, 0, 20)
            }
            assertThat(ex.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }
    }

    @Nested
    @DisplayName("getMyOrderDetail — UC-3")
    inner class GetMyOrderDetail {
        @Test
        @DisplayName("본인 주문이면 OrderResult 가 반환된다")
        fun returnsOwnOrder() {
            val user = UserFixture.validUser()
            val order = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            ).also { it.markPaid("tx", "OK") }
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findById(10L) } returns order

            val result = orderFacade.getMyOrderDetail(loginId, 10L)

            assertThat(result.status).isEqualTo(OrderStatus.PAID)
        }

        @Test
        @DisplayName("존재하지 않으면 ORDER_NOT_FOUND")
        fun notFound() {
            val user = UserFixture.validUser()
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { orderFacade.getMyOrderDetail(loginId, 99L) }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_NOT_FOUND)
        }

        @Test
        @DisplayName("타인 주문이면 ORDER_FORBIDDEN")
        fun forbidden() {
            val user = UserFixture.validUser()
            val foreignOrder = Order.create(
                userId = 99L,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            )
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findById(10L) } returns foreignOrder

            val ex = assertThrows<CoreException> { orderFacade.getMyOrderDetail(loginId, 10L) }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_FORBIDDEN)
        }
    }

    @Nested
    @DisplayName("getOrdersForAdmin — UC-4")
    inner class GetOrdersForAdmin {
        @Test
        @DisplayName("page/size 가 Repository 에 전달되고 결과는 운영 메타가 포함된 AdminOrderResult")
        fun delegatesAndIncludesAdminMeta() {
            val user = UserFixture.validUser()
            val order = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            ).also { it.markPaid("tx-9", "APPROVED") }
            every { orderRepository.findAllForAdmin(0, 20) } returns listOf(order)
            every { userRepository.findById(user.id) } returns user

            val result = orderFacade.getOrdersForAdmin(0, 20)

            assertThat(result).hasSize(1)
            assertThat(result[0].userMaskedName).endsWith("*")
            assertThat(result[0].paymentTransactionId).isEqualTo("tx-9")
            assertThat(result[0].paymentResultCode).isEqualTo("APPROVED")
        }

        @Test
        @DisplayName("결제실패 + PG 트랜잭션 누락이면 빈 값으로 응답된다")
        fun emptyMetaWhenPaymentFailedWithoutTransaction() {
            val user = UserFixture.validUser()
            val failed = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            ).also { it.markPaymentFailed(null, null) }
            every { orderRepository.findAllForAdmin(0, 20) } returns listOf(failed)
            every { userRepository.findById(user.id) } returns user

            val result = orderFacade.getOrdersForAdmin(0, 20)

            assertThat(result[0].status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            assertThat(result[0].paymentTransactionId).isNull()
            assertThat(result[0].paymentResultCode).isNull()
        }
    }

    @Nested
    @DisplayName("getOrderForAdmin — UC-5")
    inner class GetOrderForAdmin {
        @Test
        @DisplayName("어느 회원 주문이든 AdminOrderResult 반환 (FORBIDDEN 분기 없음)")
        fun returnsAnyUsersOrder() {
            val user = UserFixture.validUser()
            val order = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            ).also { it.markPaid("tx", "OK") }
            every { orderRepository.findById(10L) } returns order
            every { userRepository.findById(user.id) } returns user

            val result = orderFacade.getOrderForAdmin(10L)

            assertThat(result.orderId).isEqualTo(order.id)
        }

        @Test
        @DisplayName("존재하지 않으면 ORDER_NOT_FOUND")
        fun notFound() {
            every { orderRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { orderFacade.getOrderForAdmin(99L) }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_NOT_FOUND)
        }
    }
}
