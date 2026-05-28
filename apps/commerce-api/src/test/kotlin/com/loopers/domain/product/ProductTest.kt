package com.loopers.domain.product

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProductTest {
    @DisplayName("Product 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("price 가 음수면 예외가 발생한다.")
        @Test
        fun throwsException_whenPriceIsNegative() {
            // when
            val result = assertThrows<CoreException> {
                Product.create(
                    name = "T-shirt",
                    price = -1,
                    stock = 10,
                    likeCount = 0L,
                    brandId = 1L,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }

        @DisplayName("stock 이 음수면 예외가 발생한다.")
        @Test
        fun throwsException_whenStockIsNegative() {
            // when
            val result = assertThrows<CoreException> {
                Product.create(
                    name = "T-shirt",
                    price = 1000,
                    stock = -1,
                    likeCount = 0L,
                    brandId = 1L,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }

        @DisplayName("likeCount 가 음수면 예외가 발생한다.")
        @Test
        fun throwsException_whenLikeCountIsNegative() {
            // when
            val result = assertThrows<CoreException> {
                Product.create(
                    name = "T-shirt",
                    price = 1000,
                    stock = 10,
                    likeCount = -1L,
                    brandId = 1L,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }

        @DisplayName("name 이 blank 면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun throwsException_whenNameIsBlank(blankName: String) {
            // when
            val result = assertThrows<CoreException> {
                Product.create(
                    name = blankName,
                    price = 1000,
                    stock = 10,
                    likeCount = 0L,
                    brandId = 1L,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    inner class DeductStock {
        @DisplayName("양수 quantity 로 호출하면 stock 이 그만큼 줄어든다.")
        @Test
        fun reducesStock_whenQtyIsPositive() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            product.deductStock(3)

            // then
            assertThat(product.stock.value).isEqualTo(7)
        }

        @DisplayName("quantity 가 stock 과 같으면 호출 결과로 stock 이 0 이 된다.")
        @Test
        fun stockBecomesZero_whenQtyEqualsStock() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 5,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            product.deductStock(5)

            // then
            assertThat(product.stock.value).isEqualTo(0)
        }

        @DisplayName("quantity 가 stock 보다 크면 INSUFFICIENT_STOCK 예외가 발생한다.")
        @Test
        fun throwsInsufficientStock_whenQtyExceedsStock() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 5,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            val result = assertThrows<CoreException> {
                product.deductStock(6)
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.INSUFFICIENT_STOCK)
        }

        @DisplayName("quantity 가 0 이거나 음수면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1, -10])
        fun throwsException_whenQuantityIsZeroOrNegative(invalidQuantity: Int) {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            val result = assertThrows<CoreException> {
                product.deductStock(invalidQuantity)
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }
    }
}
