package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class CouponTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 6, 9, 12, 0, 0)

    @DisplayName("Coupon 을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("정상 입력으로 템플릿이 생성된다 (deletedAt 은 null).")
        @Test
        fun createsTemplate() {
            val coupon = Coupon.create(
                name = "신규가입 10% 할인",
                discountType = DiscountType.RATE,
                discountValue = 10,
                minOrderAmount = 10000,
                expiredAt = now.plusDays(30),
                now = now,
            )

            assertThat(coupon.name.value).isEqualTo("신규가입 10% 할인")
            assertThat(coupon.discountPolicy).isEqualTo(PercentageDiscountPolicy(10))
            assertThat(coupon.minOrderAmount).isEqualTo(10000)
            assertThat(coupon.isDeleted()).isFalse()
        }

        @DisplayName("expiredAt 이 now 이전(과거) 이면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenExpiredAtIsPast() {
            val result = assertThrows<CoreException> {
                Coupon.create(
                    name = "만료된 쿠폰",
                    discountType = DiscountType.FIXED,
                    discountValue = 1000,
                    minOrderAmount = null,
                    expiredAt = now.minusDays(1),
                    now = now,
                )
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("minOrderAmount 가 음수면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenMinOrderAmountNegative() {
            val result = assertThrows<CoreException> {
                Coupon.create(
                    name = "쿠폰",
                    discountType = DiscountType.FIXED,
                    discountValue = 1000,
                    minOrderAmount = -1,
                    expiredAt = now.plusDays(1),
                    now = now,
                )
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("minOrderAmount 가 null 이면 생성된다.")
        @Test
        fun allowsNullMinOrderAmount() {
            val coupon = Coupon.create(
                name = "쿠폰",
                discountType = DiscountType.FIXED,
                discountValue = 1000,
                minOrderAmount = null,
                expiredAt = now.plusDays(1),
                now = now,
            )
            assertThat(coupon.minOrderAmount).isNull()
        }
    }

    @DisplayName("calculateDiscount 를 호출할 때, ")
    @Nested
    inner class CalculateDiscount {
        @DisplayName("주문 합계가 minOrderAmount 이상이면 할인 금액을 반환한다.")
        @Test
        fun returnsDiscountWhenAboveMin() {
            val coupon = CouponFixture.coupon(
                discountType = DiscountType.RATE,
                discountValue = 10,
                minOrderAmount = 10000,
            )

            assertThat(coupon.calculateDiscount(20000)).isEqualTo(2000)
        }

        @DisplayName("주문 합계가 minOrderAmount 미만이면 COUPON_NOT_APPLICABLE 예외가 발생한다.")
        @Test
        fun throwsWhenBelowMin() {
            val coupon = CouponFixture.coupon(
                discountType = DiscountType.RATE,
                discountValue = 10,
                minOrderAmount = 10000,
            )

            val result = assertThrows<CoreException> { coupon.calculateDiscount(9999) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        }

        @DisplayName("minOrderAmount 가 null 이면 어떤 주문 합계든 하한 검사를 통과한다.")
        @Test
        fun passesWhenMinIsNull() {
            val coupon = CouponFixture.coupon(
                discountType = DiscountType.FIXED,
                discountValue = 1000,
                minOrderAmount = null,
            )

            assertThat(coupon.calculateDiscount(1)).isEqualTo(1)
        }
    }

    @DisplayName("isExpired 는 ")
    @Nested
    inner class IsExpired {
        @DisplayName("at 이 expiredAt 이후면 true, 같거나 이전이면 false 다.")
        @Test
        fun derivesByExpiredAt() {
            val coupon = CouponFixture.coupon(expiredAt = now)

            assertThat(coupon.isExpired(now.plusSeconds(1))).isTrue()
            assertThat(coupon.isExpired(now)).isFalse()
            assertThat(coupon.isExpired(now.minusSeconds(1))).isFalse()
        }
    }

    @DisplayName("Coupon 을 수정·삭제할 때, ")
    @Nested
    inner class UpdateAndDelete {
        @DisplayName("update 로 필드가 갱신된다.")
        @Test
        fun updatesFields() {
            val coupon = CouponFixture.coupon(name = "기존", discountType = DiscountType.RATE, discountValue = 10)

            coupon.update(
                name = "변경",
                discountType = DiscountType.FIXED,
                discountValue = 3000,
                minOrderAmount = 5000,
                expiredAt = now.plusDays(10),
                now = now,
            )

            assertThat(coupon.name.value).isEqualTo("변경")
            assertThat(coupon.discountPolicy).isEqualTo(FixedAmountDiscountPolicy(3000))
            assertThat(coupon.minOrderAmount).isEqualTo(5000)
            assertThat(coupon.expiredAt).isEqualTo(now.plusDays(10))
        }

        @DisplayName("update 도 만료 시각 과거를 거부한다.")
        @Test
        fun updateRejectsPastExpiredAt() {
            val coupon = CouponFixture.coupon()

            val result = assertThrows<CoreException> {
                coupon.update(
                    name = "변경",
                    discountType = DiscountType.FIXED,
                    discountValue = 3000,
                    minOrderAmount = null,
                    expiredAt = now.minusDays(1),
                    now = now,
                )
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("softDelete() 호출 시 deletedAt 이 설정되고 isDeleted() 가 true 다.")
        @Test
        fun softDeletes() {
            val coupon = CouponFixture.coupon()

            coupon.softDelete()

            assertThat(coupon.deletedAt).isNotNull()
            assertThat(coupon.isDeleted()).isTrue()
        }

        @DisplayName("이미 삭제된 Coupon 의 softDelete() 재호출은 멱등이다.")
        @Test
        fun softDeleteIsIdempotent() {
            val coupon = CouponFixture.coupon()
            coupon.softDelete()
            val first = coupon.deletedAt

            coupon.softDelete()

            assertThat(coupon.deletedAt).isEqualTo(first)
        }
    }
}
