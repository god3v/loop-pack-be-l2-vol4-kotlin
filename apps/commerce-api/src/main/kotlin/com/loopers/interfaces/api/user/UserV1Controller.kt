package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.domain.user.UserErrorType
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import org.springframework.web.bind.annotation.GetMapping
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
    ): ApiResponse<UserV1Dto.SignupResponse> {
        return userFacade.signup(request.toCommand())
            .let { UserV1Dto.SignupResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/me")
    override fun getMyInfo(
        @RequestHeader(name = "X-Loopers-LoginId", required = false) loginId: String?,
        @RequestHeader(name = "X-Loopers-LoginPw", required = false) password: String?,
    ): ApiResponse<UserV1Dto.MyInfoResponse> {
        if (loginId.isNullOrBlank() || password.isNullOrBlank()) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        return userFacade.getMyInfo(loginId, password)
            .let { UserV1Dto.MyInfoResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
