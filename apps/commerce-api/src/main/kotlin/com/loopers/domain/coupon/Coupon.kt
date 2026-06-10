package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import java.time.LocalDateTime

class Coupon internal constructor(
    val id: Long = 0L,
    name: CouponName,
    discountPolicy: DiscountPolicy,
    minOrderAmount: Long?,
    expiredAt: LocalDateTime,
) {
    var name: CouponName = name
        private set

    var discountPolicy: DiscountPolicy = discountPolicy
        private set

    var minOrderAmount: Long? = minOrderAmount
        private set

    var expiredAt: LocalDateTime = expiredAt
        private set

    var deletedAt: LocalDateTime? = null
        private set

    /** 주문 합계에 적용할 할인 금액을 계산한다. 최소 주문 금액 미달이면 사용 불가다. */
    fun calculateDiscount(orderAmount: Long): Long {
        val min = minOrderAmount
        if (min != null && orderAmount < min) {
            throw CoreException(CouponErrorType.COUPON_NOT_APPLICABLE, "최소 주문 금액에 못 미친다.")
        }
        return discountPolicy.discountFor(orderAmount)
    }

    fun isExpired(at: LocalDateTime): Boolean = at.isAfter(expiredAt)

    fun update(
        name: String,
        discountType: DiscountType,
        discountValue: Long,
        minOrderAmount: Long?,
        expiredAt: LocalDateTime,
        now: LocalDateTime,
    ) {
        validate(minOrderAmount, expiredAt, now)
        this.name = CouponName.of(name)
        this.discountPolicy = DiscountPolicy.of(discountType, discountValue)
        this.minOrderAmount = minOrderAmount
        this.expiredAt = expiredAt
    }

    fun softDelete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now()
        }
    }

    fun isDeleted(): Boolean = deletedAt != null

    companion object {
        private fun validate(minOrderAmount: Long?, expiredAt: LocalDateTime, now: LocalDateTime) {
            if (minOrderAmount != null && minOrderAmount < 0L) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "최소 주문 금액은 음수가 될 수 없다.")
            }
            if (!expiredAt.isAfter(now)) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "만료 시각은 미래여야 한다.")
            }
        }

        fun create(
            name: String,
            discountType: DiscountType,
            discountValue: Long,
            minOrderAmount: Long?,
            expiredAt: LocalDateTime,
            now: LocalDateTime,
        ): Coupon {
            validate(minOrderAmount, expiredAt, now)
            return Coupon(
                name = CouponName.of(name),
                discountPolicy = DiscountPolicy.of(discountType, discountValue),
                minOrderAmount = minOrderAmount,
                expiredAt = expiredAt,
            )
        }
    }
}
