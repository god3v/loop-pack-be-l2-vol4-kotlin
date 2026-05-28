package com.loopers.infrastructure.like

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface LikeJpaRepository : JpaRepository<LikeEntity, Long> {
    fun findByUserIdAndProductId(userId: Long, productId: Long): LikeEntity?
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean
    fun findAllByUserId(userId: Long, pageable: Pageable): List<LikeEntity>
}
