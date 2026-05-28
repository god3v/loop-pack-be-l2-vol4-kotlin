package com.loopers.domain.brand

object BrandFixture {
    const val DEFAULT_NAME = "애플"

    fun validBrand(name: String = DEFAULT_NAME): Brand = Brand.create(name = name)
}
