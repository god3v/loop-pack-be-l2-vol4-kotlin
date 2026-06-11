package com.loopers.application.coupon

import com.loopers.application.coupon.command.RegisterCouponCommand
import com.loopers.application.coupon.command.UpdateCouponCommand
import com.loopers.application.coupon.result.AdminCouponResult
import com.loopers.application.coupon.result.CouponIssueResult
import com.loopers.application.coupon.result.IssuedCouponResult
import com.loopers.application.coupon.result.MyCouponResult
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageQuery
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class CouponFacade(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    @Transactional
    fun issueCoupon(userId: Long, couponId: Long): IssuedCouponResult {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        coupon.ensureIssuable(LocalDateTime.now())
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw CoreException(CouponErrorType.ALREADY_ISSUED_COUPON)
        }
        val saved = userCouponRepository.save(UserCoupon.issue(userId = userId, couponId = couponId))
        return IssuedCouponResult.of(saved, coupon)
    }

    @Transactional(readOnly = true)
    fun getMyCoupons(userId: Long, pageQuery: PageQuery): PageResult<MyCouponResult> {
        val now = LocalDateTime.now()
        val page = userCouponRepository.findAllByUserId(userId, pageQuery.page, pageQuery.size)
        val coupons = couponRepository.findAllByIdsIncludingDeleted(page.content.map { it.couponId })
            .associateBy { it.id }
        return page.map { userCoupon ->
            val coupon = coupons[userCoupon.couponId]
                ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
            MyCouponResult.of(userCoupon, coupon, now)
        }
    }

    @Transactional(readOnly = true)
    fun getCouponsForAdmin(pageQuery: PageQuery): PageResult<AdminCouponResult> =
        couponRepository.findAll(pageQuery.page, pageQuery.size).map { AdminCouponResult.from(it) }

    @Transactional(readOnly = true)
    fun getCouponForAdmin(couponId: Long): AdminCouponResult {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        return AdminCouponResult.from(coupon)
    }

    @Transactional
    fun registerCoupon(command: RegisterCouponCommand): AdminCouponResult {
        val coupon = Coupon.create(
            name = command.name,
            discountType = command.discountType,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
            now = LocalDateTime.now(),
        )
        return AdminCouponResult.from(couponRepository.save(coupon))
    }

    @Transactional
    fun updateCoupon(couponId: Long, command: UpdateCouponCommand): AdminCouponResult {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        coupon.update(
            name = command.name,
            discountType = command.discountType,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
            now = LocalDateTime.now(),
        )
        return AdminCouponResult.from(couponRepository.save(coupon))
    }

    @Transactional
    fun deleteCoupon(couponId: Long) {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        coupon.softDelete()
        couponRepository.save(coupon)
    }

    @Transactional(readOnly = true)
    fun getCouponIssues(couponId: Long, pageQuery: PageQuery): PageResult<CouponIssueResult> {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        val now = LocalDateTime.now()
        return userCouponRepository.findAllByCouponId(couponId, pageQuery.page, pageQuery.size)
            .map { CouponIssueResult.of(it, coupon, now) }
    }
}
