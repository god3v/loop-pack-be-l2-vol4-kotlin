package com.loopers.application.coupon.result

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.DiscountType
import java.time.LocalDateTime

data class AdminCouponResult(
    val id: Long,
    val name: String,
    val type: DiscountType,
    val value: Long,
    val minOrderAmount: Long?,
    val expiredAt: LocalDateTime,
) {
    companion object {
        fun from(coupon: Coupon): AdminCouponResult {
            val (type, value) = coupon.discountPolicy.toTypeValue()
            return AdminCouponResult(
                id = coupon.id,
                name = coupon.name.value,
                type = type,
                value = value,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
            )
        }
    }
}
