package com.loopers.domain.product

import com.loopers.support.error.CoreException

class Product internal constructor(
    val id: Long = 0L,
    val name: ProductName,
    val price: ProductPrice,
    stock: Stock,
    val likeCount: Long,
    val brandId: Long,
) {
    var stock: Stock = stock
        private set

    init {
        if (likeCount < 0L) {
            throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "likeCount 는 음수가 될 수 없다.")
        }
    }

    fun deductStock(quantity: Int) {
        stock = stock.deduct(quantity)
    }

    companion object {
        fun create(
            name: String,
            price: Int,
            stock: Int,
            likeCount: Long,
            brandId: Long,
        ): Product = Product(
            name = ProductName.of(name),
            price = ProductPrice.of(price),
            stock = Stock.of(stock),
            likeCount = likeCount,
            brandId = brandId,
        )
    }
}
