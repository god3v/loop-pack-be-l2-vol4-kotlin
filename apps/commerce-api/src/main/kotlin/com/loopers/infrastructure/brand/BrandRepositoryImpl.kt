package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandErrorType
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageResult
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
                .orElseThrow { CoreException(BrandErrorType.BRAND_NOT_FOUND) }
                .apply { syncFrom(brand) }
        }
        return brandJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Brand? =
        brandJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAll(page: Int, size: Int): PageResult<Brand> {
        val found = brandJpaRepository.findAllBy(
            // createdAt 동률 시 페이지 간 중복/누락을 막기 위해 고유 tie-breaker(id)까지 정렬에 고정한다.
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        )
        return PageResult(
            content = found.content.map { it.toDomain() },
            page = found.number,
            size = found.size,
            totalElements = found.totalElements,
            totalPages = found.totalPages,
        )
    }

    override fun existsByName(name: String): Boolean =
        brandJpaRepository.existsByName(name)
}
