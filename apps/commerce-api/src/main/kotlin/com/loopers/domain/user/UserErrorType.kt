package com.loopers.domain.user

import com.loopers.support.error.ErrorType
import org.springframework.http.HttpStatus

enum class UserErrorType(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 로그인 ID 입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    SIGNUP_BAD_REQUEST(HttpStatus.BAD_REQUEST, "SIGNUP_BAD_REQUEST", "회원가입 입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증에 실패했습니다."),
}
