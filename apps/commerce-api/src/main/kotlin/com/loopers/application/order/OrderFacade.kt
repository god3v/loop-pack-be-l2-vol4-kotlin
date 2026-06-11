package com.loopers.application.order

import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.application.order.result.AdminOrderResult
import com.loopers.application.order.result.OrderResult
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageResult
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class OrderFacade(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun placeOrder(command: PlaceOrderCommand): OrderResult {
        val user = userRepository.findByLoginId(command.loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)

        orderRepository.findByUserIdAndIdempotencyKey(user.id, command.idempotencyKey)?.let {
            return OrderResult.from(it)
        }

        val productIds = command.lines.map { it.productId }.distinct()
        val products = productRepository.findAllByIds(productIds).associateBy { it.id }
        val quantities = command.lines.associate { it.productId to it.quantity }
        val order = OrderService.createOrder(
            userId = user.id,
            products = products,
            quantities = quantities,
            idempotencyKey = command.idempotencyKey,
        )

        // 쿠폰은 주문 1건당 1장. 같은 트랜잭션에서 발급 쿠폰을 비관적 락으로 조회해 소유·사용·만료·최소금액을
        // 검증하고 소진(USED)시킨 뒤 할인 금액을 주문에 반영한다. 어느 검증이든 실패하면 주문 전체가 롤백된다.
        // 할인 계산식·단일 사용·만료 판정 규칙은 Coupon 도메인 객체가 소유하며, 본 Facade 는 호출 순서만 조율한다.
        command.userCouponId?.let { userCouponId ->
            val userCoupon = userCouponRepository.findByIdForUpdate(userCouponId)
                ?: throw CoreException(CouponErrorType.USER_COUPON_NOT_FOUND)
            if (userCoupon.userId != user.id) {
                throw CoreException(CouponErrorType.USER_COUPON_NOT_FOUND)
            }
            if (userCoupon.isUsed()) {
                throw CoreException(CouponErrorType.ALREADY_USED_COUPON)
            }
            val coupon = couponRepository.findByIdIncludingDeleted(userCoupon.couponId)
                ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
            val now = LocalDateTime.now()
            if (coupon.isExpired(now)) {
                throw CoreException(CouponErrorType.COUPON_NOT_APPLICABLE, "만료된 쿠폰이다.")
            }
            val discount = coupon.calculateDiscount(order.originalAmount)
            userCoupon.use(now)
            userCouponRepository.save(userCoupon)
            order.applyCoupon(userCouponId, discount)
        }

        productRepository.saveAll(products.values)
        val saved = orderRepository.save(order)
        // 커밋 후 결제 처리(PG → PAID)를 트리거한다. 외부 결제 호출은 이 트랜잭션 밖(AFTER_COMMIT)에서 일어난다.
        eventPublisher.publishEvent(OrderPlacedEvent(saved.id))
        return OrderResult.from(saved)
    }

    @Transactional(readOnly = true)
    fun getMyOrders(
        loginId: String,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        page: Int,
        size: Int,
    ): PageResult<OrderResult> {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw CoreException(OrderErrorType.INVALID_DATE_RANGE)
        }
        return orderRepository
            .findAllByUserIdInPeriod(user.id, startAt, endAt, page, size)
            .map { OrderResult.from(it) }
    }

    @Transactional(readOnly = true)
    fun getMyOrderDetail(loginId: String, orderId: Long): OrderResult {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        val order = orderRepository.findById(orderId)
            ?: throw CoreException(OrderErrorType.ORDER_NOT_FOUND)
        if (order.userId != user.id) {
            throw CoreException(OrderErrorType.ORDER_FORBIDDEN)
        }
        return OrderResult.from(order)
    }

    @Transactional(readOnly = true)
    fun getOrdersForAdmin(page: Int, size: Int): PageResult<AdminOrderResult> {
        val orders = orderRepository.findAllForAdmin(page, size)
        val usersById = userRepository.findAllByIds(orders.content.map { it.userId }).associateBy { it.id }
        return orders.map { order ->
            val user = usersById[order.userId]
                ?: throw CoreException(UserErrorType.UNAUTHORIZED)
            AdminOrderResult.of(order, user)
        }
    }

    @Transactional(readOnly = true)
    fun getOrderForAdmin(orderId: Long): AdminOrderResult {
        val order = orderRepository.findById(orderId)
            ?: throw CoreException(OrderErrorType.ORDER_NOT_FOUND)
        val user = userRepository.findById(order.userId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        return AdminOrderResult.of(order, user)
    }
}
