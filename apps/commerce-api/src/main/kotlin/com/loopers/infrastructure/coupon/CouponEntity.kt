package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.domain.coupon.DiscountType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 쿠폰 템플릿 엔티티.
 *
 * `@SQLRestriction` 을 두지 않는다 — 발급 쿠폰의 사용·조회 경로는 삭제 마크된 템플릿도
 * 조회해야 하므로, 삭제 필터링은 리포지토리 메서드에서 명시적으로 처리한다.
 */
@Entity
@Table(name = "coupons")
class CouponEntity private constructor(
    name: String,
    discountType: DiscountType,
    discountValue: Long,
    minOrderAmount: Long?,
    expiredAt: LocalDateTime,
) : BaseEntity() {
    @Column(nullable = false)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    var discountType: DiscountType = discountType
        protected set

    @Column(name = "discount_value", nullable = false)
    var discountValue: Long = discountValue
        protected set

    @Column(name = "min_order_amount")
    var minOrderAmount: Long? = minOrderAmount
        protected set

    @Column(name = "expired_at", nullable = false)
    var expiredAt: LocalDateTime = expiredAt
        protected set

    fun toDomain(): Coupon = Coupon(
        id = this.id,
        name = CouponName.of(this.name),
        discountPolicy = DiscountPolicy.of(this.discountType, this.discountValue),
        minOrderAmount = this.minOrderAmount,
        expiredAt = this.expiredAt,
    )

    fun syncFrom(coupon: Coupon) {
        this.name = coupon.name.value
        this.discountType = coupon.discountPolicy.type
        this.discountValue = coupon.discountPolicy.value
        this.minOrderAmount = coupon.minOrderAmount
        this.expiredAt = coupon.expiredAt
        if (coupon.isDeleted() && this.deletedAt == null) {
            this.delete()
        }
    }

    companion object {
        fun from(coupon: Coupon): CouponEntity = CouponEntity(
            name = coupon.name.value,
            discountType = coupon.discountPolicy.type,
            discountValue = coupon.discountPolicy.value,
            minOrderAmount = coupon.minOrderAmount,
            expiredAt = coupon.expiredAt,
        )
    }
}
