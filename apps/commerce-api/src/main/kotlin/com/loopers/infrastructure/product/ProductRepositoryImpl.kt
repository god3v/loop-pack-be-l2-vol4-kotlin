package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductSortType
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageResult
import org.springframework.data.domain.Page
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
                .orElseThrow { CoreException(ProductErrorType.PRODUCT_NOT_FOUND) }
                .apply { syncFrom(product) }
        }
        return productJpaRepository.save(entity).toDomain()
    }

    override fun saveAll(products: Collection<Product>): List<Product> = products.map { save(it) }

    override fun findById(id: Long): Product? =
        productJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAllByIds(ids: Collection<Long>): List<Product> =
        if (ids.isEmpty()) emptyList() else productJpaRepository.findAllById(ids).map { it.toDomain() }

    override fun findAll(
        sort: ProductSortType,
        brandId: Long?,
        page: Int,
        size: Int,
    ): PageResult<Product> =
        findPage(brandId, PageRequest.of(page, size, sort.toJpaSort())).toPageResult()

    override fun findAllForAdmin(
        brandId: Long?,
        page: Int,
        size: Int,
    ): PageResult<Product> =
        findPage(
            brandId,
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        ).toPageResult()

    override fun findAllByBrandId(brandId: Long): List<Product> =
        productJpaRepository.findAllByBrandId(brandId).map { it.toDomain() }

    override fun existsByBrandIdAndName(brandId: Long, name: String): Boolean =
        productJpaRepository.existsByBrandIdAndName(brandId, name)

    private fun ProductSortType.toJpaSort(): Sort {
        val primary = when (this) {
            ProductSortType.LATEST -> Sort.Order.desc("createdAt")
            ProductSortType.PRICE_ASC -> Sort.Order.asc("price")
            ProductSortType.LIKES_DESC -> Sort.Order.desc("likeCount")
        }
        return Sort.by(primary, Sort.Order.desc("id"))
    }

    private fun findPage(brandId: Long?, pageRequest: PageRequest): Page<ProductEntity> =
        if (brandId == null) {
            productJpaRepository.findAll(pageRequest)
        } else {
            productJpaRepository.findAllByBrandId(brandId, pageRequest)
        }

    private fun Page<ProductEntity>.toPageResult(): PageResult<Product> = PageResult(
        content = content.map { it.toDomain() },
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
}
