package com.loopers.domain.brand

import com.loopers.support.error.CoreException

class Brand internal constructor(
    val id: Long = 0L,
    val name: String,
) {
    init {
        if (name.isBlank()) {
            throw CoreException(BrandErrorType.BRAND_BAD_REQUEST, "name 은 비어 있을 수 없다.")
        }
    }

    companion object {
        fun create(name: String): Brand = Brand(name = name)
    }
}
