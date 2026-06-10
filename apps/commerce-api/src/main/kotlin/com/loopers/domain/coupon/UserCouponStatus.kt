package com.loopers.domain.coupon

import com.loopers.support.error.CoreException

/**
 * 발급 쿠폰의 상태.
 * 저장되는 값은 [AVAILABLE] · [USED] 둘뿐이며, [EXPIRED] 는 만료 시각 경과로 조회·사용 시 파생되는 노출 상태다.
 */
enum class UserCouponStatus {
    AVAILABLE,
    USED,
    EXPIRED,
    ;

    companion object {
        fun from(value: String): UserCouponStatus =
            entries.firstOrNull { it.name == value }
                ?: throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "지원하지 않는 쿠폰 상태이다.")
    }
}
