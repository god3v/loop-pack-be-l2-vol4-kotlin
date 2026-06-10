package com.loopers.domain.coupon

import com.loopers.support.error.CoreException

/**
 * 할인 정책 — 종류별로 자기 데이터와 계산을 소유하는 다형 모델(Strategy).
 *
 * 도메인 인터페이스는 행위([discountFor])만 노출한다. 영속/와이어의 (종류, 값) 표현으로의 투영은
 * 어댑터 경계가 책임지고, 그 역(원시값 → 정책 복원)만 [of] 팩토리로 도메인이 제공한다.
 */
sealed interface DiscountPolicy {
    /** 주문 합계에 적용할 할인 금액. 음수가 될 수 없고 주문 합계를 초과하지 않는다. */
    fun discountFor(orderAmount: Long): Long

    companion object {
        /** 영속/와이어의 (종류, 값) 으로부터 정책을 복원한다. 새 종류 추가 시 컴파일러가 분기 누락을 강제한다. */
        fun of(type: DiscountType, value: Long): DiscountPolicy = when (type) {
            DiscountType.FIXED -> FixedAmountDiscountPolicy(value)
            DiscountType.RATE -> PercentageDiscountPolicy(value)
        }
    }
}

/** 정액 — 고정 금액(원) 할인. 주문 합계를 초과하지 않는다. */
data class FixedAmountDiscountPolicy(val amount: Long) : DiscountPolicy {
    init {
        if (amount <= 0L) {
            throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "정액 할인 금액은 양수여야 한다.")
        }
    }

    override fun discountFor(orderAmount: Long): Long = amount.coerceIn(0L, orderAmount)
}

/** 정률 — 비율(%, 1~100) 할인. 버림 처리하며 주문 합계를 초과하지 않는다. */
data class PercentageDiscountPolicy(val percent: Long) : DiscountPolicy {
    init {
        if (percent !in 1L..100L) {
            throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "정률 할인 비율은 1~100 범위여야 한다.")
        }
    }

    // floor(orderAmount * percent / 100) 을 Long 오버플로(조용한 0 클램프) 없이 몫/나머지로 계산한다.
    override fun discountFor(orderAmount: Long): Long =
        (orderAmount / 100 * percent + orderAmount % 100 * percent / 100).coerceIn(0L, orderAmount)
}
