package com.loopers.infrastructure.product

import com.loopers.application.product.port.ProductRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSortType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: Product): Product {
        val entity = if (product.id == 0L) {
            ProductEntity.from(product)
        } else {
            productJpaRepository.findById(product.id)
                .orElseThrow { IllegalStateException("product with id=${product.id} not found while updating") }
                .apply { syncFrom(product) }
        }
        return productJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Product? =
        productJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAll(
        sort: ProductSortType,
        brandId: Long?,
        page: Int,
        size: Int,
    ): List<Product> = productJpaRepository.findAllBy(
        brandId,
        PageRequest.of(page, size, sort.toJpaSort()),
    ).map { it.toDomain() }

    override fun findAllForAdmin(
        brandId: Long?,
        page: Int,
        size: Int,
    ): List<Product> = productJpaRepository.findAllBy(
        brandId,
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
    ).map { it.toDomain() }

    override fun findAllByBrandId(brandId: Long): List<Product> =
        productJpaRepository.findAllByBrandId(brandId).map { it.toDomain() }

    override fun existsByBrandIdAndName(brandId: Long, name: String): Boolean =
        productJpaRepository.existsByBrandIdAndName(brandId, name)

    private fun ProductSortType.toJpaSort(): Sort = when (this) {
        ProductSortType.LATEST -> Sort.by(Sort.Direction.DESC, "createdAt")
        ProductSortType.PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price")
        ProductSortType.LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount")
    }
}
