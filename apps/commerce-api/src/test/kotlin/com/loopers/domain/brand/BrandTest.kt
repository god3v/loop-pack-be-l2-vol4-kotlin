package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BrandTest {
    @DisplayName("Brand 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("name 이 blank 면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun throwsException_whenNameIsBlank(blankName: String) {
            // when
            val result = assertThrows<CoreException> {
                Brand.create(name = blankName)
            }

            // then
            assertThat(result.errorType).isEqualTo(BrandErrorType.BRAND_BAD_REQUEST)
        }
    }
}
