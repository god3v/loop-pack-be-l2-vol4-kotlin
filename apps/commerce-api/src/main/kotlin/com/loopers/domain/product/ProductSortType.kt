package com.loopers.domain.product

enum class ProductSortType(val key: String) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        fun from(value: String): ProductSortType =
            entries.first { it.key == value }
    }
}
