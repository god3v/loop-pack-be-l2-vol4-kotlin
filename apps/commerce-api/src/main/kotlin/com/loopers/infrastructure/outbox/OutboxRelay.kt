package com.loopers.infrastructure.outbox

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Transactional Outbox 릴레이 — 미발행(PENDING) 아웃박스를 주기적으로 Kafka 로 발행한다.
 * 브로커 ack(`.get()`) 이후에만 PUBLISHED 로 전이하고, 실패는 PENDING 으로 남겨 다음 주기에 재시도한다(At Least Once).
 * `aggregateId` 를 파티션 key 로 써 같은 애그리거트 이벤트의 순서를 파티션 단위로 보장한다.
 */
@Component
class OutboxRelay(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {
    private val log = LoggerFactory.getLogger(OutboxRelay::class.java)

    @Scheduled(fixedDelayString = "\${loopers.outbox.relay-interval-ms:1000}")
    @Transactional
    fun relay() {
        outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING).forEach { event ->
            runCatching {
                kafkaTemplate.send(topicOf(event.aggregateType), event.aggregateId, event.payload).get()
            }.onSuccess {
                event.markPublished(LocalDateTime.now())
            }.onFailure { e ->
                log.warn("outbox relay publish failed, keep PENDING for retry: eventId={}", event.eventId, e)
            }
        }
    }

    private fun topicOf(aggregateType: String): String = when (aggregateType) {
        "ORDER" -> ORDER_EVENTS
        "PRODUCT" -> CATALOG_EVENTS
        else -> error("unknown aggregateType for outbox routing: $aggregateType")
    }

    companion object {
        const val ORDER_EVENTS = "order-events"
        const val CATALOG_EVENTS = "catalog-events"
    }
}
