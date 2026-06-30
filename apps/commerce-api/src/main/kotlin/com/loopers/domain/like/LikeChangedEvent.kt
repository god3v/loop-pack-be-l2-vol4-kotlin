package com.loopers.domain.like

import com.loopers.support.event.DomainEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 좋아요 수 변동 사실 — `delta` 만큼 상품의 좋아요 수가 변했음을 통지한다(+1 추가 / -1 취소).
 * 집계(likeCount 반영)는 이 이벤트를 받은 리스너가 좋아요 트랜잭션과 분리해 수행한다.
 */
data class LikeChangedEvent(
    val productId: Long,
    val delta: Long,
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : DomainEvent {
    companion object {
        fun of(productId: Long, delta: Long): LikeChangedEvent =
            LikeChangedEvent(productId = productId, delta = delta)
    }
}
