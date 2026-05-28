package com.loopers.domain.order

enum class OrderStatus(val key: String) {
    PAYMENT_PENDING("payment_pending"),
    PAID("paid"),
    PAYMENT_FAILED("payment_failed"),
}
