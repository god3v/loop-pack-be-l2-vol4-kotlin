package com.loopers.application.order

import com.loopers.application.order.port.PaymentGateway
import com.loopers.application.order.port.PaymentResult
import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 결제 트리거 이후 주문 비관 락 동시성 — 같은 PENDING 주문에 pay() 가 동시·중복으로 들어와도
 * 주문 행 비관 락이 상태 전이를 직렬화해 외부 결제(charge)가 정확히 한 번만 호출되는지 검증한다.
 * (현재 운영에선 AFTER_COMMIT 트리거가 1회뿐이지만, 재시도/스윕 도입 시의 이중 청구를 막는 가드다.)
 */
@SpringBootTest
class PaymentConcurrencyIntegrationTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: com.loopers.utils.DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var paymentGateway: PaymentGateway

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("같은 PENDING 주문에 pay() 를 동시에 여러 번 호출해도, 외부 결제는 정확히 한 번만 호출되고 주문은 PAID 가 된다.")
    @Test
    fun concurrentPayChargesExactlyOnce() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = 10))
        val order = orderRepository.save(
            Order.create(
                userId = user.id,
                lines = listOf(
                    OrderLine.create(productId = product.id, productName = "운동화", unitPrice = 1000, quantity = 1),
                ),
                idempotencyKey = "pay-once",
            ),
        )

        val charges = AtomicInteger(0)
        every { paymentGateway.charge(any(), any()) } answers {
            charges.incrementAndGet()
            // 경합 창을 넓힌다 — 무락이면 늦은 스레드도 락 대기 없이 PENDING 을 읽고 중복 charge 한다.
            Thread.sleep(150)
            PaymentResult(transactionId = "tx-${order.id}", resultCode = "APPROVED", success = true)
        }

        val threads = 4
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)

        repeat(threads) {
            executor.submit {
                try {
                    startLatch.await()
                    paymentFacade.pay(order.id)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 비관 락이 pay 를 직렬화: 첫 호출만 PENDING 을 보고 charge, 나머지는 커밋된 PAID 를 보고 no-op.
        assertThat(charges.get()).isEqualTo(1)
        val persisted = orderJpaRepository.findById(order.id).get()
        assertThat(persisted.status).isEqualTo(OrderStatus.PAID)
        assertThat(persisted.paymentTransactionId).isEqualTo("tx-${order.id}")
    }

    @DisplayName("같은 결제를 동시에 여러 번 취소해도, 비관 락이 직렬화해 재고 복원은 정확히 한 번만 일어난다.")
    @Test
    fun concurrentCancelRestoresExactlyOnce() {
        val user = userRepository.save(UserFixture.validUser())
        // 직접 저장한 주문은 placeOrder 와 달리 재고를 차감하지 않으므로, 이미 차감된 상태(9)를 가정한다.
        // 취소 보상이 라인 수량(1)만큼 되돌려 10 이 되어야 한다(이중 복원이면 11+).
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = 9))
        val order = orderRepository.save(
            Order.create(
                userId = user.id,
                lines = listOf(
                    OrderLine.create(productId = product.id, productName = "운동화", unitPrice = 1000, quantity = 1),
                ),
                idempotencyKey = "cancel-once",
            ),
        )
        every { paymentGateway.charge(any(), any()) } returns PaymentResult("tx-${order.id}", "APPROVED", true)
        paymentFacade.pay(order.id) // 주문 PAID, 결제 APPROVED (재고는 그대로 9).
        val payment = requireNotNull(paymentRepository.findLatestByOrderId(order.id))

        // 환불 호출 창을 넓혀 동시 취소 경합을 강제한다 — 무락이면 늦은 스레드도 APPROVED 를 읽고 이중 복원한다.
        every { paymentGateway.refund(any(), any()) } answers {
            Thread.sleep(150)
            PaymentResult("refund-${order.id}", "CANCELED", true)
        }

        val threads = 4
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            executor.submit {
                try {
                    startLatch.await()
                    paymentFacade.cancel(payment.id)
                } catch (_: Exception) {
                    // 경합 패자/멱등 no-op 경로의 예외는 무시 — 정합성은 아래 최종 상태로 검증한다.
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 비관 락이 취소를 직렬화: 첫 취소만 보상(9→10), 나머지는 CANCELED 를 보고 no-op → 재고는 10(이중 복원 11+ 아님).
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(10)
        assertThat(orderJpaRepository.findById(order.id).get().status).isEqualTo(OrderStatus.CANCELED)
        assertThat(requireNotNull(paymentRepository.findById(payment.id)).status).isEqualTo(PaymentStatus.CANCELED)
    }
}
