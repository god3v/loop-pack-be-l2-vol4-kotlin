package com.loopers.application.order

import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderService
import com.loopers.application.order.result.AdminOrderResult
import com.loopers.application.order.result.OrderResult
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserRepository
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.user.UserErrorType
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class OrderFacade(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
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

        productRepository.saveAll(products.values)
        return OrderResult.from(orderRepository.save(order))
    }

    @Transactional(readOnly = true)
    fun getMyOrders(
        loginId: String,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        page: Int,
        size: Int,
    ): List<OrderResult> {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        return orderRepository
            .findAllByUserIdAndOrderedAtBetween(
                user.id,
                startAt ?: LocalDateTime.MIN,
                endAt ?: LocalDateTime.MAX,
                page,
                size,
            )
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
    fun getOrdersForAdmin(page: Int, size: Int): List<AdminOrderResult> {
        val orders = orderRepository.findAllForAdmin(page, size)
        val usersById = userRepository.findAllByIds(orders.map { it.userId }).associateBy { it.id }
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
