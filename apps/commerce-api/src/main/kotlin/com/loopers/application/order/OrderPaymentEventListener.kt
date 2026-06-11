package com.loopers.application.order

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderPaymentEventListener(
    private val paymentFacade: PaymentFacade,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderPlaced(event: OrderPlacedEvent) {
        paymentFacade.pay(event.orderId)
    }
}
