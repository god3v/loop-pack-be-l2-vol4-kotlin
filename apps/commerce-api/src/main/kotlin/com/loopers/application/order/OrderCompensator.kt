package com.loopers.application.order

import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.Order
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component

/**
 * 주문 보상 — 차감했던 재고를 비관 쓰기 락으로 잡아 복원하고, 소진한 쿠폰을 AVAILABLE 로 되돌린다.
 * 결제 실패 정산(`PaymentFacade.settle`) 이 사용한다. Facade 가 아닌 application 협력 컴포넌트라 호출자의 트랜잭션 경계 안에서 동작한다.
 */
@Component
class OrderCompensator(
    private val productRepository: ProductRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    fun restore(order: Order) {
        // 재고 복원은 신규 차감과 경합하므로 차감과 동일한 비관 락으로 직렬화한다(lost update 방지).
        // 주문 스냅샷에 박힌 상품은 delist(soft-delete) 되어도 재고를 되돌려야 하므로 삭제 마크 행까지 함께 잠근다.
        // 그래도 행 자체가 없으면(주문엔 있는데 DB 엔 없는 진짜 정합성 손상) 부분 보상 대신 즉시 실패시켜 트랜잭션을 롤백한다.
        val products = productRepository.findAllByIdsForUpdateIncludingDeleted(
            order.lines.map { it.productId },
        ).associateBy { it.id }
        order.lines.forEach { line ->
            val product = products[line.productId]
                ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND, "보상 대상 상품(${line.productId})을 찾을 수 없어 재고를 복원할 수 없다.")
            product.restoreStock(line.quantity.value)
        }
        productRepository.saveAll(products.values)

        // 적용 쿠폰도 누락되면 USED 로 영영 남으므로 조용히 끝내지 않고 실패시킨다.
        order.userCouponId?.let { userCouponId ->
            val userCoupon = userCouponRepository.findByIdForUpdate(userCouponId)
                ?: throw CoreException(CouponErrorType.USER_COUPON_NOT_FOUND, "보상 대상 쿠폰($userCouponId)을 찾을 수 없어 사용 취소할 수 없다.")
            userCoupon.cancelUse()
            userCouponRepository.save(userCoupon)
        }
    }
}
