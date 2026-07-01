package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.kafka.EventEnvelope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Outbox 릴레이 — 미발행(PENDING) 아웃박스를 aggregateType 별 토픽에 aggregateId 를 key(파티션 순서 보장)로,
 * `EventEnvelope` 봉투로 감싸 발행하고, 브로커 ack 이후에만 PUBLISHED 로 전이한다.
 * 발행 실패 시 PENDING 을 유지해 다음 릴레이가 재시도한다(At Least Once).
 */
class OutboxRelayTest {
    private val outboxEventJpaRepository = mockk<OutboxEventJpaRepository>(relaxed = true)
    private val kafkaTemplate = mockk<KafkaTemplate<Any, Any>>()
    private val objectMapper = ObjectMapper()
    private val relay = OutboxRelay(outboxEventJpaRepository, kafkaTemplate, objectMapper)

    @Test
    fun `PENDING 아웃박스를 aggregateType 별 토픽에 aggregateId key 로 봉투에 담아 발행하고 PUBLISHED 로 전이한다`() {
        val order = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = """{"orderId":42,"userId":1}""")
        val like = outboxRow(aggregateType = "PRODUCT", aggregateId = "7", eventType = "LIKE_CREATED", payload = """{"productId":7}""")
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING) } returns listOf(order, like)
        val envelopes = mutableListOf<Any>()
        every { kafkaTemplate.send(any<String>(), any(), capture(envelopes)) } returns CompletableFuture.completedFuture(mockk())

        relay.relay()

        verify { kafkaTemplate.send("order-events", "42", any()) }
        verify { kafkaTemplate.send("catalog-events", "7", any()) }
        val orderEnvelope = envelopes.map { it as EventEnvelope }.first { it.aggregateId == "42" }
        assertThat(orderEnvelope.eventType).isEqualTo("ORDER_CREATED")
        assertThat(orderEnvelope.aggregateType).isEqualTo("ORDER")
        assertThat(orderEnvelope.payload.get("orderId").asInt()).isEqualTo(42)
        assertThat(order.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(order.publishedAt).isNotNull()
        assertThat(like.status).isEqualTo(OutboxStatus.PUBLISHED)
    }

    @Test
    fun `발행이 실패하면 PENDING 으로 남겨 다음 릴레이에서 재시도한다`() {
        val order = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = "{}")
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING) } returns listOf(order)
        every { kafkaTemplate.send(any<String>(), any(), any()) } throws RuntimeException("broker down")

        relay.relay()

        assertThat(order.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(order.publishedAt).isNull()
    }

    private fun outboxRow(aggregateType: String, aggregateId: String, eventType: String, payload: String) =
        OutboxEventEntity.create(
            eventId = UUID.randomUUID(),
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            occurredAt = LocalDateTime.now(),
        )
}
