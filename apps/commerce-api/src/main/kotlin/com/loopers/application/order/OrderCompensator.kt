package com.loopers.application.order

import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.Order
import com.loopers.domain.product.ProductRepository
import org.springframework.stereotype.Component

/**
 * 주문 보상 — 차감했던 재고를 비관 쓰기 락(id ASC)으로 잡아 복원하고, 소진한 쿠폰을 AVAILABLE 로 되돌린다.
 * 결제 실패 정산(`PaymentSettler`)과 취소/환불(`PaymentCanceler`) 이 공유한다. 호출자의 트랜잭션 경계 안에서 동작한다.
 */
@Component
class OrderCompensator(
    private val productRepository: ProductRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    fun restore(order: Order) {
        // 재고 복원은 신규 차감과 경합하므로 차감과 동일한 비관 락으로 직렬화한다(lost update 방지).
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
