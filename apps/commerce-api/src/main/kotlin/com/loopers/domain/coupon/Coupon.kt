package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import java.time.LocalDateTime

class Coupon internal constructor(
    val id: Long = 0L,
    name: CouponName,
    discountPolicy: DiscountPolicy,
    minOrderAmount: Long?,
    issueStartAt: LocalDateTime,
    issueEndAt: LocalDateTime,
    useStartAt: LocalDateTime,
    useEndAt: LocalDateTime,
) {
    var name: CouponName = name
        private set

    var discountPolicy: DiscountPolicy = discountPolicy
        private set

    var minOrderAmount: Long? = minOrderAmount
        private set

    var issueStartAt: LocalDateTime = issueStartAt
        private set

    var issueEndAt: LocalDateTime = issueEndAt
        private set

    var useStartAt: LocalDateTime = useStartAt
        private set

    var useEndAt: LocalDateTime = useEndAt
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

    fun ensureIssuable(now: LocalDateTime) {
        if (now.isBefore(issueStartAt) || now.isAfter(issueEndAt)) {
            throw CoreException(CouponErrorType.COUPON_NOT_APPLICABLE, "발급 가능 기간이 아니다.")
        }
    }

    fun update(
        name: String,
        discountType: DiscountType,
        discountValue: Long,
        minOrderAmount: Long?,
        issueStartAt: LocalDateTime,
        issueEndAt: LocalDateTime,
        useStartAt: LocalDateTime,
        useEndAt: LocalDateTime,
        now: LocalDateTime,
    ) {
        validate(minOrderAmount, issueStartAt, issueEndAt, useStartAt, useEndAt, now)
        this.name = CouponName.of(name)
        this.discountPolicy = DiscountPolicy.of(discountType, discountValue)
        this.minOrderAmount = minOrderAmount
        this.issueStartAt = issueStartAt
        this.issueEndAt = issueEndAt
        this.useStartAt = useStartAt
        this.useEndAt = useEndAt
    }

    fun softDelete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now()
        }
    }

    fun isDeleted(): Boolean = deletedAt != null

    companion object {
        private fun validate(
            minOrderAmount: Long?,
            issueStartAt: LocalDateTime,
            issueEndAt: LocalDateTime,
            useStartAt: LocalDateTime,
            useEndAt: LocalDateTime,
            now: LocalDateTime,
        ) {
            if (minOrderAmount != null && minOrderAmount < 0L) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "최소 주문 금액은 음수가 될 수 없다.")
            }
            if (!issueEndAt.isAfter(issueStartAt)) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "발급 종료 시각은 발급 시작 시각 이후여야 한다.")
            }
            if (!useEndAt.isAfter(useStartAt)) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "사용 종료 시각은 사용 시작 시각 이후여야 한다.")
            }
            if (!issueEndAt.isAfter(now)) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "발급 종료 시각은 미래여야 한다.")
            }
        }

        fun create(
            name: String,
            discountType: DiscountType,
            discountValue: Long,
            minOrderAmount: Long?,
            issueStartAt: LocalDateTime,
            issueEndAt: LocalDateTime,
            useStartAt: LocalDateTime,
            useEndAt: LocalDateTime,
            now: LocalDateTime,
        ): Coupon {
            validate(minOrderAmount, issueStartAt, issueEndAt, useStartAt, useEndAt, now)
            return Coupon(
                name = CouponName.of(name),
                discountPolicy = DiscountPolicy.of(discountType, discountValue),
                minOrderAmount = minOrderAmount,
                issueStartAt = issueStartAt,
                issueEndAt = issueEndAt,
                useStartAt = useStartAt,
                useEndAt = useEndAt,
            )
        }
    }
}
