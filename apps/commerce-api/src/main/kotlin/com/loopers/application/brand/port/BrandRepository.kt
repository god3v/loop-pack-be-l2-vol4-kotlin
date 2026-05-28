package com.loopers.application.brand.port

import com.loopers.domain.brand.Brand

interface BrandRepository {
    fun save(brand: Brand): Brand
    fun findById(id: Long): Brand?
    fun findAll(page: Int, size: Int): List<Brand>
    fun existsByName(name: String): Boolean
}
