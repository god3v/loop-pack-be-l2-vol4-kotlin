package com.loopers.infrastructure.brand

import com.loopers.application.brand.port.BrandRepository
import com.loopers.domain.brand.Brand
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: Brand): Brand {
        val entity = if (brand.id == 0L) {
            BrandEntity.from(brand)
        } else {
            brandJpaRepository.findById(brand.id)
                .orElseThrow { IllegalStateException("brand with id=${brand.id} not found while updating") }
                .apply { syncFrom(brand) }
        }
        return brandJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Brand? =
        brandJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAll(page: Int, size: Int): List<Brand> =
        brandJpaRepository.findAllBy(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        ).map { it.toDomain() }

    override fun existsByName(name: String): Boolean =
        brandJpaRepository.existsByName(name)
}
