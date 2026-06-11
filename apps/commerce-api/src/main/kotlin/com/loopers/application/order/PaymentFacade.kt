package com.loopers.application.order

import com.loopers.application.order.port.PaymentGateway
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 결제 오케스트레이션. 주문 저장 트랜잭션 커밋 후(이벤트 리스너가 트리거) 별도 트랜잭션에서 실행된다.
 *
 * 외부 결제를 호출하고 결과를 주문에 반영한다 — 성공이면 PAID, 실패면 같은 트랜잭션에서 **보상**(차감했던 재고 원복 +
 * 소진한 쿠폰 복원) 후 PAYMENT_FAILED 로 전이한다. 도메인 상태 전이는 각 애그리거트(Order·Product·UserCoupon)가
 * 소유하고, 본 Facade 는 트랜잭션 경계와 호출 순서만 조율한다.
 */
@Component
class PaymentFacade(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val userCouponRepository: UserCouponRepository,
    private val paymentGateway: PaymentGateway,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun pay(orderId: Long) {
        // 주문 행을 비관 락으로 잡아 상태 전이(PENDING→PAID/FAILED)를 원자화한다 — 동시/중복 pay 의 이중 청구·이중 보상 방지.
        val order = orderRepository.findByIdForUpdate(orderId) ?: return
        if (order.status != OrderStatus.PAYMENT_PENDING) return

        val result = paymentGateway.charge(order.id, order.totalAmount)
        if (result.success) {
            order.markPaid(result.transactionId, result.resultCode)
        } else {
            compensate(order)
            order.markPaymentFailed(result.transactionId, result.resultCode)
        }
        orderRepository.save(order)
    }

    private fun compensate(order: Order) {
        // 재고 복원은 신규 주문의 재고 차감과 경합한다 — 차감과 동일한 비관 쓰기 락(id ASC)으로 상품 행을 잡아
        // read-modify-write 의 lost update(복원이 동시 차감을 덮어쓰는 오버셀)를 막는다.
        val products = productRepository.findAllByIdsForUpdate(order.lines.map { it.productId }).associateBy { it.id }
        order.lines.forEach { line -> products[line.productId]?.restoreStock(line.quantity.value) }
        productRepository.saveAll(products.values)

        order.userCouponId?.let { userCouponId ->
            userCouponRepository.findByIdForUpdate(userCouponId)?.let { userCoupon ->
                userCoupon.cancelUse()
                userCouponRepository.save(userCoupon)
            }
        }
    }
}
