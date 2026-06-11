package com.loopers.application.coupon.command

import com.loopers.domain.coupon.DiscountType
import java.time.LocalDateTime

data class RegisterCouponCommand(
    val name: String,
    val discountType: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long?,
    val expiredAt: LocalDateTime,
)
