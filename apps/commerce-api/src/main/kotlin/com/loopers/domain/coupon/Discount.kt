package com.loopers.domain.coupon

import com.loopers.support.error.CoreException

/**
 * 할인 정책 값 객체 — 종류(정액/정률) 와 값을 묶어 할인 금액 계산을 캡슐화한다.
 *
 * - 정액(FIXED): 할인 금액(원). 주문 합계를 초과하지 않는다.
 * - 정률(RATE): 비율(%, 1~100). `주문 합계 × 비율 / 100` 을 버림 처리하며 주문 합계를 초과하지 않는다.
 */
class Discount private constructor(
    val type: DiscountType,
    val value: Long,
) {
    fun amountFor(orderAmount: Long): Long {
        val raw = when (type) {
            DiscountType.FIXED -> value
            // floor(orderAmount * value / 100) 와 동일하되, 큰 합계에서 orderAmount * value 의
            // Long 오버플로(조용한 0 클램프) 를 피하도록 몫/나머지로 분해해 계산한다.
            DiscountType.RATE -> orderAmount / 100 * value + orderAmount % 100 * value / 100
        }
        return raw.coerceIn(0L, orderAmount)
    }

    companion object {
        fun of(type: DiscountType, value: Long): Discount {
            if (value <= 0L) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "할인 값은 양수여야 한다.")
            }
            if (type == DiscountType.RATE && value !in 1L..100L) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "정률 할인 값은 1~100 범위여야 한다.")
            }
            return Discount(type, value)
        }
    }
}
