package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 발급 쿠폰 — 회원이 [Coupon] 템플릿으로부터 발급받아 소유하는 인스턴스.
 * 최대 한 번 사용되며, 템플릿은 [couponId] 로 ID 참조한다.
 */
class UserCoupon internal constructor(
    val id: Long = 0L,
    val userId: Long,
    val couponId: Long,
    status: UserCouponStatus = UserCouponStatus.AVAILABLE,
    val issuedAt: LocalDateTime,
    usedAt: LocalDateTime? = null,
) {
    var status: UserCouponStatus = status
        private set

    var usedAt: LocalDateTime? = usedAt
        private set

    fun use(at: LocalDateTime) {
        if (status == UserCouponStatus.USED) {
            throw CoreException(CouponErrorType.ALREADY_USED_COUPON, "이미 사용된 쿠폰이다.")
        }
        status = UserCouponStatus.USED
        usedAt = at
    }

    fun isUsed(): Boolean = status == UserCouponStatus.USED

    /** 템플릿의 만료 여부를 합쳐 노출 상태(AVAILABLE/USED/EXPIRED) 를 파생한다. 사용 완료가 만료보다 우선한다. */
    fun viewStatus(coupon: Coupon, at: LocalDateTime): UserCouponStatus = when {
        status == UserCouponStatus.USED -> UserCouponStatus.USED
        coupon.isExpired(at) -> UserCouponStatus.EXPIRED
        else -> UserCouponStatus.AVAILABLE
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")

        fun issue(userId: Long, couponId: Long): UserCoupon = UserCoupon(
            userId = userId,
            couponId = couponId,
            status = UserCouponStatus.AVAILABLE,
            issuedAt = LocalDateTime.now(SEOUL),
        )
    }
}
