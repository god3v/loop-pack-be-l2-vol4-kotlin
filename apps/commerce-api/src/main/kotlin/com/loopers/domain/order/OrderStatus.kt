package com.loopers.domain.order

enum class OrderStatus(val key: String) {
    PAID("paid"),
    PAYMENT_FAILED("payment_failed"),
}
