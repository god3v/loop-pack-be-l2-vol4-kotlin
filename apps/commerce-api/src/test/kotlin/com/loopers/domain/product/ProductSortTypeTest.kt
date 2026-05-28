package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ProductSortTypeTest {
    @DisplayName("from(허용 문자열) 호출 시 대응 enum 으로 매핑된다.")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "latest, LATEST",
        "price_asc, PRICE_ASC",
        "likes_desc, LIKES_DESC",
    )
    fun mapsFromKey(key: String, expected: ProductSortType) {
        assertThat(ProductSortType.from(key)).isEqualTo(expected)
    }
}
