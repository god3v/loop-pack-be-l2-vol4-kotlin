package com.loopers.application.order

import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.application.order.port.OrderRepository
import com.loopers.application.order.port.PaymentGateway
import com.loopers.application.order.result.AdminOrderResult
import com.loopers.application.order.result.OrderResult
import com.loopers.application.product.port.ProductRepository
import com.loopers.application.user.port.UserRepository
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderLine
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.user.UserErrorType
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class OrderFacade(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
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
        command.lines.forEach { lineCommand ->
            if (products[lineCommand.productId] == null) {
                throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
            }
        }
        command.lines.forEach { lineCommand ->
            products[lineCommand.productId]!!.deductStock(lineCommand.quantity)
        }
        val lines = command.lines.map { lineCommand ->
            val product = products.getValue(lineCommand.productId)
            OrderLine.create(
                productId = lineCommand.productId,
                productName = product.name.value,
                unitPrice = product.price.value,
                quantity = lineCommand.quantity,
            )
        }

        val order = Order.create(
            userId = user.id,
            lines = lines,
            idempotencyKey = command.idempotencyKey,
            couponId = command.couponId,
        )

        val paymentResult = paymentGateway.charge(orderId = order.id, amount = order.totalAmount)
        if (!paymentResult.success) {
            command.lines.forEach { lineCommand ->
                products.getValue(lineCommand.productId).restoreStock(lineCommand.quantity)
            }
            productRepository.saveAll(products.values)
            order.markPaymentFailed(paymentResult.transactionId, paymentResult.resultCode)
            orderRepository.save(order)
            throw CoreException(OrderErrorType.PAYMENT_FAILED)
        }

        productRepository.saveAll(products.values)
        order.markPaid(
            transactionId = paymentResult.transactionId ?: "",
            resultCode = paymentResult.resultCode ?: "",
        )
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
        val (resolvedStart, resolvedEnd) = resolveWindow(startAt, endAt)
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw CoreException(OrderErrorType.INVALID_DATE_RANGE)
        }
        return orderRepository
            .findAllByUserIdAndOrderedAtBetween(user.id, resolvedStart, resolvedEnd, page, size)
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
    fun getOrdersForAdmin(page: Int, size: Int): List<AdminOrderResult> =
        orderRepository.findAllForAdmin(page, size).map { order ->
            val user = userRepository.findById(order.userId)
                ?: throw CoreException(UserErrorType.UNAUTHORIZED)
            AdminOrderResult.of(order, user)
        }

    @Transactional(readOnly = true)
    fun getOrderForAdmin(orderId: Long): AdminOrderResult {
        val order = orderRepository.findById(orderId)
            ?: throw CoreException(OrderErrorType.ORDER_NOT_FOUND)
        val user = userRepository.findById(order.userId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        return AdminOrderResult.of(order, user)
    }

    private fun resolveWindow(
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
    ): Pair<LocalDateTime, LocalDateTime> {
        if (startAt == null && endAt == null) {
            val today = LocalDateTime.now(SEOUL)
            return today.minusDays(30) to today
        }
        return (startAt ?: LocalDateTime.MIN) to (endAt ?: LocalDateTime.MAX)
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
