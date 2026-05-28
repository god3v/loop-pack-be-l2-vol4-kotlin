package com.loopers.infrastructure.product

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductEntity, Long> {
    @Query(
        """
        SELECT p FROM ProductEntity p
        WHERE (:brandId IS NULL OR p.brandId = :brandId)
        """,
    )
    fun findAllBy(
        @Param("brandId") brandId: Long?,
        pageable: Pageable,
    ): List<ProductEntity>

    fun findAllByBrandId(brandId: Long): List<ProductEntity>

    fun existsByBrandIdAndName(brandId: Long, name: String): Boolean
}
