package com.loopers.application.coupon.result

import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.FixedAmountDiscountPolicy
import com.loopers.domain.coupon.PercentageDiscountPolicy

/**
 * 결과 DTO 가 와이어로 노출하는 (할인 종류, 값) 투영.
 * 도메인 인터페이스가 아니라 출력 경계(application 결과)가 소유한다 — `DiscountPolicy.of` 의 역방향.
 */
internal fun DiscountPolicy.toTypeValue(): Pair<DiscountType, Long> = when (this) {
    is FixedAmountDiscountPolicy -> DiscountType.FIXED to amount
    is PercentageDiscountPolicy -> DiscountType.RATE to percent
}
