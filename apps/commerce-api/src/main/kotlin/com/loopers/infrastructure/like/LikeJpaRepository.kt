package com.loopers.infrastructure.like

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LikeJpaRepository : JpaRepository<LikeEntity, Long> {
    fun findByUserIdAndProductId(userId: Long, productId: Long): LikeEntity?
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean

    // 단일 DELETE 문 — 파생 deleteBy 는 조회 행 수를 반환해(동시 요청 모두 1) unlike 감소 게이트를 무력화한다.
    // 실제 삭제 행 수(패자는 0)를 반환하도록 직접 쿼리로 바꾼다.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM LikeEntity l WHERE l.userId = :userId AND l.productId = :productId")
    fun deleteByUserIdAndProductId(@Param("userId") userId: Long, @Param("productId") productId: Long): Int

    fun findAllByUserId(userId: Long, pageable: Pageable): Page<LikeEntity>
}
