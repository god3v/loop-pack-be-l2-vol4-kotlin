package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.command.RegisterCouponCommand
import com.loopers.application.coupon.command.UpdateCouponCommand
import com.loopers.application.coupon.result.AdminCouponResult
import com.loopers.application.coupon.result.CouponIssueResult
import com.loopers.application.coupon.result.IssuedCouponResult
import com.loopers.application.coupon.result.MyCouponResult
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.support.page.PageResult
import java.time.LocalDateTime

/**
 * 쿠폰 API DTO.
 *
 * 와이어의 `type`(FIXED/RATE) · `status`(AVAILABLE/USED/EXPIRED) 는 도메인 enum 이름(대문자) 을 그대로 쓴다.
 * - 응답: enum 을 그대로 노출해 Jackson 이 이름으로 직렬화한다.
 * - 요청: `type` 은 String 으로 받아 [DiscountType.from] 으로 역매핑한다(미지원 값 → `COUPON_BAD_REQUEST`).
 */
class CouponV1Dto {
    data class RegisterCouponRequest(
        val name: String,
        val type: String,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    ) {
        fun toCommand(): RegisterCouponCommand = RegisterCouponCommand(
            name = name,
            discountType = DiscountType.from(type),
            discountValue = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
    }

    data class UpdateCouponRequest(
        val name: String,
        val type: String,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    ) {
        fun toCommand(): UpdateCouponCommand = UpdateCouponCommand(
            name = name,
            discountType = DiscountType.from(type),
            discountValue = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
    }

    data class IssuedCouponResponse(
        val userCouponId: Long,
        val couponId: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
        val status: UserCouponStatus,
        val issuedAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: IssuedCouponResult): IssuedCouponResponse = IssuedCouponResponse(
                userCouponId = result.userCouponId,
                couponId = result.couponId,
                name = result.name,
                type = result.type,
                value = result.value,
                minOrderAmount = result.minOrderAmount,
                expiredAt = result.expiredAt,
                status = result.status,
                issuedAt = result.issuedAt,
            )
        }
    }

    data class MyCouponItem(
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
            fun from(result: MyCouponResult): MyCouponItem = MyCouponItem(
                userCouponId = result.userCouponId,
                couponId = result.couponId,
                name = result.name,
                type = result.type,
                value = result.value,
                minOrderAmount = result.minOrderAmount,
                expiredAt = result.expiredAt,
                status = result.status,
                issuedAt = result.issuedAt,
                usedAt = result.usedAt,
            )
        }
    }

    data class MyCouponsResponse(
        val content: List<MyCouponItem>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<MyCouponResult>): MyCouponsResponse = MyCouponsResponse(
                content = page.content.map { MyCouponItem.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    data class AdminCouponResponse(
        val id: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: AdminCouponResult): AdminCouponResponse = AdminCouponResponse(
                id = result.id,
                name = result.name,
                type = result.type,
                value = result.value,
                minOrderAmount = result.minOrderAmount,
                expiredAt = result.expiredAt,
            )
        }
    }

    data class AdminCouponsResponse(
        val content: List<AdminCouponResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<AdminCouponResult>): AdminCouponsResponse = AdminCouponsResponse(
                content = page.content.map { AdminCouponResponse.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    data class CouponIssueItem(
        val userCouponId: Long,
        val userId: Long,
        val status: UserCouponStatus,
        val issuedAt: LocalDateTime,
        val usedAt: LocalDateTime?,
    ) {
        companion object {
            fun from(result: CouponIssueResult): CouponIssueItem = CouponIssueItem(
                userCouponId = result.userCouponId,
                userId = result.userId,
                status = result.status,
                issuedAt = result.issuedAt,
                usedAt = result.usedAt,
            )
        }
    }

    data class CouponIssuesResponse(
        val content: List<CouponIssueItem>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<CouponIssueResult>): CouponIssuesResponse = CouponIssuesResponse(
                content = page.content.map { CouponIssueItem.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }
}
