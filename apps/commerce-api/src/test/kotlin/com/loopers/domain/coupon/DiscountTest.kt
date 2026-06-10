package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DiscountTest {
    @DisplayName("Discount 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("FIXED 의 value 가 0 이하면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0, -1, -1000])
        fun throwsWhenFixedValueNonPositive(value: Long) {
            val result = assertThrows<CoreException> { Discount.of(DiscountType.FIXED, value) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("RATE 의 value 가 1 미만 또는 100 초과면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0, -5, 101, 200])
        fun throwsWhenRateValueOutOfRange(value: Long) {
            val result = assertThrows<CoreException> { Discount.of(DiscountType.RATE, value) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }
    }

    @DisplayName("amountFor 를 계산할 때, ")
    @Nested
    inner class AmountFor {
        @DisplayName("FIXED 는 할인 금액이지만 주문 합계를 초과하지 않는다.")
        @Test
        fun fixedCapsAtOrderAmount() {
            val discount = Discount.of(DiscountType.FIXED, 5000)

            assertThat(discount.amountFor(10000)).isEqualTo(5000)
            assertThat(discount.amountFor(3000)).isEqualTo(3000)
        }

        @DisplayName("RATE 는 주문 합계 × 비율 / 100 을 버림한 값이다.")
        @Test
        fun rateFloors() {
            val discount = Discount.of(DiscountType.RATE, 10)

            // 9999 * 10 / 100 = 999.9 -> 999 (버림)
            assertThat(discount.amountFor(9999)).isEqualTo(999)
            assertThat(discount.amountFor(10000)).isEqualTo(1000)
        }

        @DisplayName("RATE 100% 는 주문 합계를 초과하지 않는다.")
        @Test
        fun rateDoesNotExceedOrderAmount() {
            val discount = Discount.of(DiscountType.RATE, 100)

            assertThat(discount.amountFor(7000)).isEqualTo(7000)
        }

        @DisplayName("어떤 종류든 할인 금액은 음수가 아니다.")
        @Test
        fun neverNegative() {
            assertThat(Discount.of(DiscountType.FIXED, 5000).amountFor(0)).isEqualTo(0)
            assertThat(Discount.of(DiscountType.RATE, 10).amountFor(0)).isEqualTo(0)
        }

        @DisplayName("RATE 는 매우 큰 주문 합계에서도 오버플로로 0 이 되지 않고 floor(합계 × 비율 / 100) 을 유지한다.")
        @Test
        fun rateDoesNotOverflowOnHugeOrderAmount() {
            // orderAmount * value 가 Long 오버플로를 일으키는 크기(1e18 * 10 > Long.MAX) 에서도 정확해야 한다.
            val discount = Discount.of(DiscountType.RATE, 10)
            val orderAmount = 1_000_000_000_000_000_000L // 1e18

            assertThat(discount.amountFor(orderAmount)).isEqualTo(100_000_000_000_000_000L) // 1e17
        }
    }
}
