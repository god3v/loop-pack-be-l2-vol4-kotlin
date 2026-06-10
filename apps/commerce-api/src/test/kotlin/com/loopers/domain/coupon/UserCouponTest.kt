package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class UserCouponTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 6, 9, 12, 0, 0)

    @DisplayName("발급할 때, ")
    @Nested
    inner class Issue {
        @DisplayName("issue() 로 AVAILABLE 상태의 발급 쿠폰이 만들어진다.")
        @Test
        fun issuesAvailable() {
            val userCoupon = UserCoupon.issue(userId = 1L, couponId = 7L)

            assertThat(userCoupon.userId).isEqualTo(1L)
            assertThat(userCoupon.couponId).isEqualTo(7L)
            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.AVAILABLE)
            assertThat(userCoupon.usedAt).isNull()
        }
    }

    @DisplayName("사용할 때, ")
    @Nested
    inner class Use {
        @DisplayName("use(at) 호출 시 USED 로 전이되고 usedAt 이 채워진다.")
        @Test
        fun transitionsToUsed() {
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.AVAILABLE)

            userCoupon.use(now)

            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED)
            assertThat(userCoupon.usedAt).isEqualTo(now)
        }

        @DisplayName("이미 USED 인 쿠폰의 use(at) 는 ALREADY_USED_COUPON 예외가 발생한다.")
        @Test
        fun throwsWhenAlreadyUsed() {
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.USED, usedAt = now.minusDays(1))

            val result = assertThrows<CoreException> { userCoupon.use(now) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.ALREADY_USED_COUPON)
        }
    }

    @DisplayName("노출 상태를 파생할 때, ")
    @Nested
    inner class ViewStatus {
        @DisplayName("USED 면 만료 시각을 지나도 USED 로 노출된다 (사용 우선).")
        @Test
        fun usedTakesPrecedence() {
            val coupon = CouponFixture.coupon(expiredAt = now.minusDays(1))
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.USED, usedAt = now.minusDays(2))

            assertThat(userCoupon.viewStatus(coupon, now)).isEqualTo(UserCouponStatus.USED)
        }

        @DisplayName("AVAILABLE + 만료 경과면 EXPIRED 로 노출된다.")
        @Test
        fun derivesExpired() {
            val coupon = CouponFixture.coupon(expiredAt = now.minusSeconds(1))
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.AVAILABLE)

            assertThat(userCoupon.viewStatus(coupon, now)).isEqualTo(UserCouponStatus.EXPIRED)
        }

        @DisplayName("AVAILABLE + 만료 전이면 AVAILABLE 로 노출된다.")
        @Test
        fun staysAvailable() {
            val coupon = CouponFixture.coupon(expiredAt = now.plusDays(1))
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.AVAILABLE)

            assertThat(userCoupon.viewStatus(coupon, now)).isEqualTo(UserCouponStatus.AVAILABLE)
        }
    }
}
