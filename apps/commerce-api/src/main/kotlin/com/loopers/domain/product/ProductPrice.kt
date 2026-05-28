package com.loopers.domain.product

import com.loopers.support.error.CoreException

@JvmInline
value class ProductPrice private constructor(val value: Int) {
    companion object {
        fun of(value: Int): ProductPrice {
            if (value < 0) {
                throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "price 는 음수가 될 수 없다.")
            }
            return ProductPrice(value)
        }
    }
}
