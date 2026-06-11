package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class CouponTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 6, 9, 12, 0, 0)

    private fun create(
        minOrderAmount: Long? = 10000,
        issueStartAt: LocalDateTime = now.minusDays(1),
        issueEndAt: LocalDateTime = now.plusDays(30),
        useStartAt: LocalDateTime = now,
        useEndAt: LocalDateTime = now.plusDays(60),
    ): Coupon = Coupon.create(
        name = "신규가입 10% 할인",
        discountType = DiscountType.RATE,
        discountValue = 10,
        minOrderAmount = minOrderAmount,
        issueStartAt = issueStartAt,
        issueEndAt = issueEndAt,
        useStartAt = useStartAt,
        useEndAt = useEndAt,
        now = now,
    )

    @DisplayName("Coupon 을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("정상 입력으로 템플릿이 생성된다 (deletedAt 은 null).")
        @Test
        fun createsTemplate() {
            val coupon = create(minOrderAmount = 10000)

            assertThat(coupon.name.value).isEqualTo("신규가입 10% 할인")
            assertThat(coupon.discountPolicy).isEqualTo(PercentageDiscountPolicy(10))
            assertThat(coupon.minOrderAmount).isEqualTo(10000)
            assertThat(coupon.issueStartAt).isEqualTo(now.minusDays(1))
            assertThat(coupon.issueEndAt).isEqualTo(now.plusDays(30))
            assertThat(coupon.useStartAt).isEqualTo(now)
            assertThat(coupon.useEndAt).isEqualTo(now.plusDays(60))
            assertThat(coupon.isDeleted()).isFalse()
        }

        @DisplayName("발급 종료가 발급 시작 이후가 아니면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenIssueWindowInverted() {
            val result = assertThrows<CoreException> {
                create(issueStartAt = now.plusDays(5), issueEndAt = now.plusDays(5))
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("사용 종료가 사용 시작 이후가 아니면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenUseWindowInverted() {
            val result = assertThrows<CoreException> {
                create(useStartAt = now.plusDays(10), useEndAt = now.plusDays(1))
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("발급 종료가 과거(now 이전) 면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenIssueEndIsPast() {
            val result = assertThrows<CoreException> {
                create(issueStartAt = now.minusDays(10), issueEndAt = now.minusDays(1))
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("minOrderAmount 가 음수면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenMinOrderAmountNegative() {
            val result = assertThrows<CoreException> { create(minOrderAmount = -1) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("minOrderAmount 가 null 이면 생성된다.")
        @Test
        fun allowsNullMinOrderAmount() {
            assertThat(create(minOrderAmount = null).minOrderAmount).isNull()
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

    @DisplayName("ensureIssuable 은 ")
    @Nested
    inner class EnsureIssuable {
        @DisplayName("발급 가능 구간 안(경계 포함) 이면 예외 없이 통과한다.")
        @Test
        fun passesWithinIssueWindow() {
            val coupon = CouponFixture.coupon(issueStartAt = now.minusDays(1), issueEndAt = now.plusDays(1))

            assertThatCode { coupon.ensureIssuable(now) }.doesNotThrowAnyException()
            assertThatCode { coupon.ensureIssuable(now.minusDays(1)) }.doesNotThrowAnyException()
            assertThatCode { coupon.ensureIssuable(now.plusDays(1)) }.doesNotThrowAnyException()
        }

        @DisplayName("발급 시작 전이면 COUPON_NOT_APPLICABLE 예외가 발생한다.")
        @Test
        fun throwsBeforeIssueStart() {
            val coupon = CouponFixture.coupon(issueStartAt = now.plusDays(1), issueEndAt = now.plusDays(10))

            val result = assertThrows<CoreException> { coupon.ensureIssuable(now) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        }

        @DisplayName("발급 종료 후면 COUPON_NOT_APPLICABLE 예외가 발생한다.")
        @Test
        fun throwsAfterIssueEnd() {
            val coupon = CouponFixture.coupon(issueStartAt = now.minusDays(10), issueEndAt = now.minusSeconds(1))

            val result = assertThrows<CoreException> { coupon.ensureIssuable(now) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
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
                issueStartAt = now,
                issueEndAt = now.plusDays(10),
                useStartAt = now,
                useEndAt = now.plusDays(20),
                now = now,
            )

            assertThat(coupon.name.value).isEqualTo("변경")
            assertThat(coupon.discountPolicy).isEqualTo(FixedAmountDiscountPolicy(3000))
            assertThat(coupon.minOrderAmount).isEqualTo(5000)
            assertThat(coupon.useEndAt).isEqualTo(now.plusDays(20))
        }

        @DisplayName("update 도 발급 종료 과거를 거부한다.")
        @Test
        fun updateRejectsPastIssueEnd() {
            val coupon = CouponFixture.coupon()

            val result = assertThrows<CoreException> {
                coupon.update(
                    name = "변경",
                    discountType = DiscountType.FIXED,
                    discountValue = 3000,
                    minOrderAmount = null,
                    issueStartAt = now.minusDays(10),
                    issueEndAt = now.minusDays(1),
                    useStartAt = now,
                    useEndAt = now.plusDays(20),
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
