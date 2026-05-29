package com.loopers.domain.product

interface ProductRepository {
    fun save(product: Product): Product
    fun saveAll(products: Collection<Product>): List<Product>
    fun findById(id: Long): Product?
    fun findAllByIds(ids: Collection<Long>): List<Product>
    fun findAll(
        sort: ProductSortType,
        brandId: Long?,
        page: Int,
        size: Int,
    ): List<Product>

    fun findAllForAdmin(
        brandId: Long?,
        page: Int,
        size: Int,
    ): List<Product>

    fun findAllByBrandId(brandId: Long): List<Product>

    fun existsByBrandIdAndName(brandId: Long, name: String): Boolean
}
