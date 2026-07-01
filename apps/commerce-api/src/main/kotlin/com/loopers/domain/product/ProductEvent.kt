package com.loopers.domain.product

import com.loopers.support.event.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

sealed class ProductEvent : DomainEvent {
    abstract val productId: Long

    data class Viewed(
        override val productId: Long,
        val userId: Long?,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : ProductEvent()
}
