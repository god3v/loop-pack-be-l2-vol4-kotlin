package com.loopers.infrastructure.payment

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    // 정산·취소 경로 전용 — 비관적 쓰기 락(SELECT ... FOR UPDATE). 동시/중복 정산의 상태 전이를 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): PaymentEntity?

    // 주문의 최신 결제 1건(id 내림차순). 진행 중 결제 dedupe 에 사용.
    fun findFirstByOrderIdOrderByIdDesc(orderId: Long): PaymentEntity?
}
