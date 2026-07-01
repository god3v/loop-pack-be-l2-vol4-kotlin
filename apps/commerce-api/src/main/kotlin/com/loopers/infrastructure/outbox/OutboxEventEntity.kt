package com.loopers.infrastructure.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Transactional Outbox 레코드 — 순수 인프라 기술 구현. 도메인 소비자가 없어 별도 POJO/포트를 두지 않고 엔티티로 둔다.
 * 도메인 변경과 같은 트랜잭션에서 브리지가 적재하고, 릴레이가 Kafka 로 발행한 뒤 상태를 전이한다.
 * `payload` 는 발행 이벤트를 직렬화한 봉투이며, `eventId` 는 Consumer 멱등(`event_handled`) 키로 전달된다.
 */
@Entity
@Table(name = "outbox")
class OutboxEventEntity private constructor(
    eventId: String,
    aggregateType: String,
    aggregateId: String,
    eventType: String,
    payload: String,
    occurredAt: LocalDateTime,
) : BaseEntity() {
    @Column(name = "event_id", nullable = false, unique = true, updatable = false, length = 36)
    var eventId: String = eventId
        protected set

    @Column(name = "aggregate_type", nullable = false, updatable = false)
    var aggregateType: String = aggregateType
        protected set

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    var aggregateId: String = aggregateId
        protected set

    @Column(name = "event_type", nullable = false, updatable = false)
    var eventType: String = eventType
        protected set

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    var payload: String = payload
        protected set

    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: LocalDateTime = occurredAt
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxStatus = OutboxStatus.PENDING
        protected set

    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null
        protected set

    fun markPublished(at: LocalDateTime) {
        status = OutboxStatus.PUBLISHED
        publishedAt = at
    }

    fun markFailed() {
        status = OutboxStatus.FAILED
    }

    companion object {
        fun create(
            eventId: UUID,
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            payload: String,
            occurredAt: LocalDateTime,
        ): OutboxEventEntity = OutboxEventEntity(
            eventId = eventId.toString(),
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            occurredAt = occurredAt,
        )
    }
}
