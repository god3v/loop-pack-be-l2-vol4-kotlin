package com.loopers.infrastructure.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<ProductEntity, Long> {
    // 브랜드 필터가 있는 목록 조회. 전체 조회는 JpaRepository.findAll(Pageable) 을 그대로 쓴다.
    // 두 경로 모두 @SQLRestriction(deleted_at IS NULL) 이 본문·count 쿼리에 적용된다.
    fun findAllByBrandId(brandId: Long, pageable: Pageable): Page<ProductEntity>

    fun findAllByBrandId(brandId: Long): List<ProductEntity>

    fun existsByBrandIdAndName(brandId: Long, name: String): Boolean
}
