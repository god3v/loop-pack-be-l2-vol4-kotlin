package com.loopers.domain.like

import com.loopers.support.event.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

sealed class LikeEvent : DomainEvent {
    abstract val productId: Long

    data class Created(
        override val productId: Long,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : LikeEvent()

    data class Canceled(
        override val productId: Long,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : LikeEvent()
}
