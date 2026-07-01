package com.loopers.infrastructure.outbox

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
 * Outbox 릴레이 — 미발행(PENDING) 아웃박스를 aggregateType 별 토픽에 aggregateId 를 key(파티션 순서 보장)로 발행하고,
 * 브로커 ack 이후에만 PUBLISHED 로 전이한다. 발행 실패 시 PENDING 을 유지해 다음 릴레이가 재시도한다(At Least Once).
 */
class OutboxRelayTest {
    private val outboxEventJpaRepository = mockk<OutboxEventJpaRepository>(relaxed = true)
    private val kafkaTemplate = mockk<KafkaTemplate<Any, Any>>()
    private val relay = OutboxRelay(outboxEventJpaRepository, kafkaTemplate)

    @Test
    fun `PENDING 아웃박스를 aggregateType 별 토픽에 aggregateId 를 key 로 발행하고 PUBLISHED 로 전이한다`() {
        val order = outboxRow(aggregateType = "ORDER", aggregateId = "42", payload = """{"orderId":42}""")
        val like = outboxRow(aggregateType = "PRODUCT", aggregateId = "7", payload = """{"productId":7}""")
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING) } returns listOf(order, like)
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns CompletableFuture.completedFuture(mockk())

        relay.relay()

        verify { kafkaTemplate.send("order-events", "42", """{"orderId":42}""") }
        verify { kafkaTemplate.send("catalog-events", "7", """{"productId":7}""") }
        assertThat(order.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(order.publishedAt).isNotNull()
        assertThat(like.status).isEqualTo(OutboxStatus.PUBLISHED)
    }

    @Test
    fun `발행이 실패하면 PENDING 으로 남겨 다음 릴레이에서 재시도한다`() {
        val order = outboxRow(aggregateType = "ORDER", aggregateId = "42", payload = "{}")
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING) } returns listOf(order)
        every { kafkaTemplate.send(any<String>(), any(), any()) } throws RuntimeException("broker down")

        relay.relay()

        assertThat(order.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(order.publishedAt).isNull()
    }

    private fun outboxRow(aggregateType: String, aggregateId: String, payload: String) =
        OutboxEventEntity.create(
            eventId = UUID.randomUUID(),
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = "com.loopers.SomeEvent",
            payload = payload,
            occurredAt = LocalDateTime.now(),
        )
}
