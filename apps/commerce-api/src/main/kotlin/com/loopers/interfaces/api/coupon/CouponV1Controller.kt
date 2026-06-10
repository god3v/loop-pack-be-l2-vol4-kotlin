package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.interfaces.api.auth.LoginUser
import com.loopers.interfaces.api.auth.RequireAuth
import com.loopers.support.page.PageQuery
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class CouponV1Controller(
    private val couponFacade: CouponFacade,
) : CouponV1ApiSpec {
    @PostMapping("/coupons/{couponId}/issue")
    @RequireAuth
    override fun issueCoupon(
        @LoginUser user: AuthUser,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.IssuedCouponResponse> {
        val result = couponFacade.issueCoupon(user.id, couponId)
        return ApiResponse.success(CouponV1Dto.IssuedCouponResponse.from(result))
    }

    @GetMapping("/users/me/coupons")
    @RequireAuth
    override fun getMyCoupons(
        @LoginUser user: AuthUser,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<CouponV1Dto.MyCouponsResponse> {
        val result = couponFacade.getMyCoupons(
            user.id,
            PageQuery(page = pageable.pageNumber, size = pageable.pageSize),
        )
        return ApiResponse.success(CouponV1Dto.MyCouponsResponse.from(result))
    }
}
