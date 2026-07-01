package com.loopers.infrastructure.outbox

import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, Long> {
    fun findByStatusOrderByIdAsc(status: OutboxStatus): List<OutboxEventEntity>
}
