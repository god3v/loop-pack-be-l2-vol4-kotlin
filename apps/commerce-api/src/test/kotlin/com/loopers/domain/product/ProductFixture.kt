package com.loopers.domain.product

object ProductFixture {
    const val DEFAULT_NAME = "맥북 프로 14인치"
    const val DEFAULT_PRICE = 2_500_000
    const val DEFAULT_STOCK = 10
    const val DEFAULT_LIKE_COUNT = 0L
    const val DEFAULT_BRAND_ID = 1L

    fun validProduct(
        name: String = DEFAULT_NAME,
        price: Int = DEFAULT_PRICE,
        stock: Int = DEFAULT_STOCK,
        likeCount: Long = DEFAULT_LIKE_COUNT,
        brandId: Long = DEFAULT_BRAND_ID,
    ): Product = Product.create(
        name = name,
        price = price,
        stock = stock,
        likeCount = likeCount,
        brandId = brandId,
    )
}
