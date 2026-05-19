package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.domain.user.UserErrorType
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @PostMapping
    override fun signup(
        @RequestBody request: UserV1Dto.SignupRequest,
    ): ApiResponse<Any> {
        userFacade.signup(request.toCommand())
        return ApiResponse.success()
    }

    @GetMapping("/me")
    override fun getMyInfo(
        @RequestHeader(name = "X-Loopers-LoginId", required = false) loginId: String?,
        @RequestHeader(name = "X-Loopers-LoginPw", required = false) password: String?,
    ): ApiResponse<UserV1Dto.MyInfoResponse> {
        val (id, pw) = requireAuthHeaders(loginId, password)
        return userFacade.getMyInfo(id, pw)
            .let { UserV1Dto.MyInfoResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PatchMapping("/me/password")
    override fun changePassword(
        @RequestHeader(name = "X-Loopers-LoginId", required = false) loginId: String?,
        @RequestHeader(name = "X-Loopers-LoginPw", required = false) password: String?,
        @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Any> {
        val (id, pw) = requireAuthHeaders(loginId, password)
        userFacade.changePassword(request.toCommand(id, pw))
        return ApiResponse.success()
    }

    /*
    TODO 추후 인터셉터로 인증 책임 분리
     */
    private fun requireAuthHeaders(loginId: String?, password: String?): Pair<String, String> {
        if (loginId.isNullOrBlank() || password.isNullOrBlank()) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        return loginId to password
    }
}
