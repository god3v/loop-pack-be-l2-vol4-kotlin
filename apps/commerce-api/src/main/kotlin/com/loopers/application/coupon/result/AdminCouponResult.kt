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
        fun from(coupon: Coupon): AdminCouponResult = AdminCouponResult(
            id = coupon.id,
            name = coupon.name.value,
            type = coupon.discount.type,
            value = coupon.discount.value,
            minOrderAmount = coupon.minOrderAmount,
            expiredAt = coupon.expiredAt,
        )
    }
}
