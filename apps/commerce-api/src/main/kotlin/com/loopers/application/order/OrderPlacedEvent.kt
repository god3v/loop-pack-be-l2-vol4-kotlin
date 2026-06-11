package com.loopers.application.order

/**
 * 주문이 결제대기(PAYMENT_PENDING) 로 저장된 직후 발행되는 이벤트.
 * 트랜잭션 커밋 후 결제 처리(PG 호출 → PAID 전이) 의 트리거다.
 */
data class OrderPlacedEvent(val orderId: Long)
