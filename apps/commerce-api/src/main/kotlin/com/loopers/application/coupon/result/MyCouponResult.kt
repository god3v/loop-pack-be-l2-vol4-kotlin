package com.loopers.application.coupon.result

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponStatus
import java.time.LocalDateTime

data class MyCouponResult(
    val userCouponId: Long,
    val couponId: Long,
    val name: String,
    val type: DiscountType,
    val value: Long,
    val minOrderAmount: Long?,
    val expiredAt: LocalDateTime,
    val status: UserCouponStatus,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime?,
) {
    companion object {
        fun of(userCoupon: UserCoupon, coupon: Coupon, at: LocalDateTime): MyCouponResult = MyCouponResult(
            userCouponId = userCoupon.id,
            couponId = coupon.id,
            name = coupon.name.value,
            type = coupon.discountPolicy.type,
            value = coupon.discountPolicy.value,
            minOrderAmount = coupon.minOrderAmount,
            expiredAt = coupon.expiredAt,
            status = userCoupon.viewStatus(coupon, at),
            issuedAt = userCoupon.issuedAt,
            usedAt = userCoupon.usedAt,
        )
    }
}
