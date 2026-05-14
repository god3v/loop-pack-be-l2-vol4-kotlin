package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "Loopers 사용자(User) API 입니다.")
interface UserV1ApiSpec {
    @Operation(
        summary = "회원가입",
        description = "회원 정보를 입력하여 신규 회원을 등록합니다.",
    )
    fun signup(request: UserV1Dto.SignupRequest): ApiResponse<UserV1Dto.SignupResponse>

    @Operation(
        summary = "내 정보 조회",
        description = "회원의 정보를 조회합니다.",
    )
    fun getMyInfo(loginId: String?, password: String?): ApiResponse<UserV1Dto.MyInfoResponse>

    @Operation(
        summary = "비밀번호 수정",
        description = "회원의 비밀번호를 수정합니다.",
    )
    fun changePassword(
        loginId: String?,
        password: String?,
        request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Any>
}
