package com.loopers.application.order

import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.application.order.port.PaymentGateway
import com.loopers.application.order.port.PaymentResult
import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 결제 실패 보상 통합 — PaymentGateway 를 실패로 모킹해, 커밋 후 결제가 실패하면
 * placeOrder 트랜잭션에서 차감/소진된 재고·쿠폰이 같은 보상 트랜잭션으로 원복되고 주문이 PAYMENT_FAILED 가 되는지 검증한다.
 */
@SpringBootTest
class OrderPaymentFailureIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: com.loopers.utils.DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var paymentGateway: PaymentGateway

    @BeforeEach
    fun setUp() {
        // 모든 결제를 실패로 떨어뜨린다.
        every { paymentGateway.charge(any(), any()) } returns PaymentResult("tx-fail", "DECLINED", false)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("결제가 실패하면, 차감된 재고와 소진된 쿠폰이 원복되고 주문이 PAYMENT_FAILED 가 된다.")
    @Test
    fun compensatesStockAndCouponOnPaymentFailure() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = 10))
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
                idempotencyKey = "order-pay-fail",
                userCouponId = userCoupon.id,
                lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
            ),
        )

        // 주문은 PAYMENT_FAILED 로 마감되고, 보상으로 재고(10)·쿠폰(AVAILABLE) 이 원복된다.
        val persisted = orderJpaRepository.findById(result.orderId).get()
        assertThat(persisted.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(10)
        assertThat(userCouponRepository.findById(userCoupon.id)!!.status).isEqualTo(UserCouponStatus.AVAILABLE)
    }

    @DisplayName("같은 상품에 결제 실패 주문 N건을 동시에 처리해도, 보상 재고 복원이 신규 차감과 경합하지 않고 재고가 보존된다.")
    @Test
    fun concurrentCompensationsPreserveStock() {
        val user = userRepository.save(UserFixture.validUser())
        // 각 주문이 1 차감 후(결제 실패) 1 복원 → 순효과 0. 재고는 넉넉히 둬 차감 실패(INSUFFICIENT_STOCK) 변수를 배제하고
        // 복원↔차감 경합(직렬화) 자체에 집중한다.
        val initialStock = 10
        val product = productRepository.save(
            ProductFixture.validProduct(name = "운동화", price = 1000, stock = initialStock),
        )

        // pay() 가 AFTER_COMMIT 안에서 REQUIRES_NEW 로 실행돼 스레드당 커넥션 2개(보류 tx1 + 보상 tx2)를
        // 점유하므로, 풀(10) 고갈을 피하도록 스레드 수를 보수적으로 잡는다.
        val threads = 4
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)

        repeat(threads) { i ->
            executor.submit {
                try {
                    startLatch.await()
                    // 결제는 @MockkBean 으로 항상 실패 → tx1 에서 1 차감, AFTER_COMMIT 보상 tx 에서 1 복원.
                    orderFacade.placeOrder(
                        PlaceOrderCommand(
                            loginId = user.loginId,
                            idempotencyKey = "concurrent-fail-$i",
                            userCouponId = null,
                            lines = listOf(OrderLineCommand(productId = product.id, quantity = 1)),
                        ),
                    )
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 차감과 복원이 같은 비관 락으로 직렬화되므로, lost update 없이 재고가 초기값으로 보존된다.
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(initialStock)
        // N건 모두 주문은 생성되고 결제 실패로 마감된다.
        assertThat(orderJpaRepository.count()).isEqualTo(threads.toLong())
        assertThat(orderJpaRepository.findAll()).allMatch { it.status == OrderStatus.PAYMENT_FAILED }
    }
}
