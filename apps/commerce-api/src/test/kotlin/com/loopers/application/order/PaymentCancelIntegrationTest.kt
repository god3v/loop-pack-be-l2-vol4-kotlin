package com.loopers.application.order

import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentErrorType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

/**
 * 결제 취소(환불) 통합 — 성공 결제(PAID)된 주문을 취소하면 결제·주문이 CANCELED 로 전이하고
 * 차감됐던 재고와 소진된 쿠폰이 원복되는지 실제 영속 계층으로 검증한다(운영 어댑터는 항상 성공 = 환불도 성공).
 */
@SpringBootTest
class PaymentCancelIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val paymentFacade: PaymentFacade,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: com.loopers.utils.DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("결제 완료된 주문을 취소하면, 결제·주문이 CANCELED 가 되고 재고·쿠폰이 원복된다.")
    @Test
    fun cancelsPaidOrderAndRestoresStockAndCoupon() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(com.loopers.domain.product.ProductFixture.validProduct(name = "운동화", price = 1000, stock = 10))
        val template = couponRepository.save(
            CouponFixture.coupon(name = "10% 할인", discountType = DiscountType.RATE, discountValue = 10, minOrderAmount = null),
        )
        val userCoupon = userCouponRepository.save(
            UserCoupon.issue(
                userId = user.id,
                couponId = template.id,
                usableFrom = LocalDateTime.now().minusDays(1),
                expiredAt = LocalDateTime.now().plusDays(30),
            ),
        )

        // 결제 성공(AlwaysSuccess) → 주문 PAID, 결제 APPROVED, 재고 8, 쿠폰 USED.
        val result = orderFacade.placeOrder(
            PlaceOrderCommand(
                loginId = user.loginId,
                idempotencyKey = "order-cancel",
                userCouponId = userCoupon.id,
                lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
            ),
        )
        val payment = requireNotNull(paymentRepository.findLatestByOrderId(result.orderId))
        assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(8)

        paymentFacade.cancel(payment.id)

        // 결제·주문 CANCELED, 재고(10)·쿠폰(AVAILABLE) 원복.
        assertThat(requireNotNull(paymentRepository.findById(payment.id)).status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(orderJpaRepository.findById(result.orderId).get().status).isEqualTo(OrderStatus.CANCELED)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(10)
        assertThat(userCouponRepository.findById(userCoupon.id)!!.status).isEqualTo(UserCouponStatus.AVAILABLE)
    }

    @DisplayName("이미 CANCELED 인 결제를 다시 취소해도 재고·쿠폰이 중복 복원되지 않는다 (멱등).")
    @Test
    fun reCancelDoesNotRestoreTwice() {
        val placed = placePaidOrderWithCoupon(idempotencyKey = "order-cancel-idem")
        // 1차 취소 — 재고 10, 쿠폰 AVAILABLE 로 원복.
        paymentFacade.cancel(placed.paymentId)

        // 2차 취소 — 멱등 no-op 이어야 한다(재고가 12 로 이중 복원되거나 예외가 나면 안 된다).
        paymentFacade.cancel(placed.paymentId)

        assertThat(requireNotNull(paymentRepository.findById(placed.paymentId)).status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(productRepository.findById(placed.productId)!!.stock.value).isEqualTo(10)
        assertThat(userCouponRepository.findById(placed.userCouponId)!!.status).isEqualTo(UserCouponStatus.AVAILABLE)
    }

    @DisplayName("존재하지 않는 결제를 취소하면 PAYMENT_NOT_FOUND 로 실패한다.")
    @Test
    fun cancelMissingPaymentThrows() {
        val ex = assertThrows<CoreException> { paymentFacade.cancel(999_999L) }

        assertThat(ex.errorType).isEqualTo(PaymentErrorType.PAYMENT_NOT_FOUND)
    }

    private data class PaidOrder(val productId: Long, val userCouponId: Long, val orderId: Long, val paymentId: Long)

    /** 쿠폰 적용 + 재고 차감된 PAID 주문(결제 APPROVED) 을 만들어 식별자를 돌려준다. */
    private fun placePaidOrderWithCoupon(idempotencyKey: String): PaidOrder {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(
            com.loopers.domain.product.ProductFixture.validProduct(name = "운동화", price = 1000, stock = 10),
        )
        val template = couponRepository.save(
            CouponFixture.coupon(name = "10% 할인", discountType = DiscountType.RATE, discountValue = 10, minOrderAmount = null),
        )
        val userCoupon = userCouponRepository.save(
            UserCoupon.issue(
                userId = user.id,
                couponId = template.id,
                usableFrom = LocalDateTime.now().minusDays(1),
                expiredAt = LocalDateTime.now().plusDays(30),
            ),
        )
        val result = orderFacade.placeOrder(
            PlaceOrderCommand(
                loginId = user.loginId,
                idempotencyKey = idempotencyKey,
                userCouponId = userCoupon.id,
                lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
            ),
        )
        val payment = requireNotNull(paymentRepository.findLatestByOrderId(result.orderId))
        return PaidOrder(product.id, userCoupon.id, result.orderId, payment.id)
    }
}
