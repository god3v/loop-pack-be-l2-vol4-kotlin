package com.loopers.application.order

import com.loopers.application.order.port.PaymentGateway
import com.loopers.application.order.port.PaymentResult
import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PaymentFacade")
class PaymentFacadeTest {
    private val orderRepository: OrderRepository = mockk()
    private val productRepository: ProductRepository = mockk()
    private val userCouponRepository: UserCouponRepository = mockk()
    private val paymentGateway: PaymentGateway = mockk()
    private val paymentFacade = PaymentFacade(orderRepository, productRepository, userCouponRepository, paymentGateway)

    private fun pendingOrder(userCouponId: Long? = null): Order {
        val order = Order.create(
            userId = 1L,
            lines = listOf(OrderLine.create(productId = 1L, productName = "운동화", unitPrice = 1000, quantity = 2)),
            idempotencyKey = "key-1",
        )
        if (userCouponId != null) order.applyCoupon(userCouponId, 0L)
        return order
    }

    @DisplayName("결제 성공 시")
    @Nested
    inner class OnSuccess {
        @Test
        @DisplayName("주문이 PAID 로 전이·저장되고, 보상(재고·쿠폰)은 일어나지 않는다")
        fun marksPaidWithoutCompensation() {
            val order = pendingOrder(userCouponId = 99L)
            every { orderRepository.findById(1L) } returns order
            every { paymentGateway.charge(any(), any()) } returns PaymentResult("tx-1", "APPROVED", true)
            every { orderRepository.save(any()) } answers { firstArg() }

            paymentFacade.pay(1L)

            assertThat(order.status).isEqualTo(OrderStatus.PAID)
            assertThat(order.paymentTransactionId).isEqualTo("tx-1")
            verify(exactly = 0) { productRepository.saveAll(any()) }
            verify(exactly = 0) { userCouponRepository.save(any()) }
            verify { orderRepository.save(order) }
        }
    }

    @DisplayName("결제 실패 시")
    @Nested
    inner class OnFailure {
        @Test
        @DisplayName("재고를 원복하고 쿠폰을 AVAILABLE 로 되돌린 뒤 PAYMENT_FAILED 로 전이한다")
        fun compensatesAndMarksFailed() {
            val order = pendingOrder(userCouponId = 99L)
            val product = ProductFixture.validProduct(id = 1L, name = "운동화", price = 1000, stock = 10)
            val userCoupon = CouponFixture.userCoupon(id = 99L, userId = 1L, couponId = 7L, status = UserCouponStatus.USED)
            every { orderRepository.findById(1L) } returns order
            every { paymentGateway.charge(any(), any()) } returns PaymentResult("tx-f", "DECLINED", false)
            every { productRepository.findAllByIds(listOf(1L)) } returns listOf(product)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            every { userCouponRepository.findById(99L) } returns userCoupon
            every { userCouponRepository.save(any()) } answers { firstArg() }
            every { orderRepository.save(any()) } answers { firstArg() }

            paymentFacade.pay(1L)

            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            assertThat(product.stock.value).isEqualTo(12) // 10 + 라인 수량 2 원복
            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.AVAILABLE)
            verify { productRepository.saveAll(any()) }
            verify { userCouponRepository.save(userCoupon) }
        }

        @Test
        @DisplayName("쿠폰 미적용 주문은 재고만 원복한다 (쿠폰 조회 없음)")
        fun compensatesStockOnlyWhenNoCoupon() {
            val order = pendingOrder(userCouponId = null)
            val product = ProductFixture.validProduct(id = 1L, name = "운동화", price = 1000, stock = 10)
            every { orderRepository.findById(1L) } returns order
            every { paymentGateway.charge(any(), any()) } returns PaymentResult("tx-f", "DECLINED", false)
            every { productRepository.findAllByIds(listOf(1L)) } returns listOf(product)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            every { orderRepository.save(any()) } answers { firstArg() }

            paymentFacade.pay(1L)

            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            assertThat(product.stock.value).isEqualTo(12)
            verify(exactly = 0) { userCouponRepository.findById(any()) }
        }
    }

    @DisplayName("멱등 / 방어")
    @Nested
    inner class Idempotency {
        @Test
        @DisplayName("이미 PAID 인 주문은 결제하지 않는다 (중복 트리거 no-op)")
        fun skipsWhenAlreadyPaid() {
            val order = pendingOrder().also { it.markPaid("tx-prev", "APPROVED") }
            every { orderRepository.findById(1L) } returns order

            paymentFacade.pay(1L)

            verify(exactly = 0) { paymentGateway.charge(any(), any()) }
            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        @DisplayName("주문이 없으면 결제하지 않는다")
        fun skipsWhenOrderMissing() {
            every { orderRepository.findById(404L) } returns null

            paymentFacade.pay(404L)

            verify(exactly = 0) { paymentGateway.charge(any(), any()) }
        }
    }
}
