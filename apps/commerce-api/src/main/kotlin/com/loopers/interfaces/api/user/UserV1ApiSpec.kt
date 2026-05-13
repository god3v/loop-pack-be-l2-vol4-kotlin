package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "Loopers 사용자(User) API 입니다.")
interface UserV1ApiSpec {
    @Operation(
        summary = "회원가입",
        description = "loginId/password/name/birthDate/email 로 회원을 신규 가입한다.",
    )
    fun signup(request: UserV1Dto.SignupRequest): ApiResponse<UserV1Dto.SignupResponse>
}
