package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?

    /** 결제 정산·취소 경로 전용 — 비관적 쓰기 락으로 조회한다. 동시 정산 직렬화로 이중 처리 방지. */
    fun findByIdForUpdate(id: Long): Payment?

    /** 주문의 가장 최근 결제 — 진행 중(REQUESTED/APPROVED) 결제 중복 생성 방어용(1주문 N시도). */
    fun findLatestByOrderId(orderId: Long): Payment?
}
