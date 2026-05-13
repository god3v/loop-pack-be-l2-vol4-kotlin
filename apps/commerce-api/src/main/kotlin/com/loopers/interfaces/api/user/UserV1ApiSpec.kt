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

    @Operation(
        summary = "내 정보 조회",
        description = "X-Loopers-LoginId / X-Loopers-LoginPw 헤더 인증으로 본인 정보를 조회한다. name 은 마지막 글자가 마스킹된다.",
    )
    fun getMyInfo(loginId: String?, password: String?): ApiResponse<UserV1Dto.MyInfoResponse>
}
