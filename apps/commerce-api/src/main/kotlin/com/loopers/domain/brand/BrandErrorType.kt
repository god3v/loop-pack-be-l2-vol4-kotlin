package com.loopers.domain.brand

import com.loopers.support.error.ErrorType
import org.springframework.http.HttpStatus

enum class BrandErrorType(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    BRAND_BAD_REQUEST(HttpStatus.BAD_REQUEST, "BRAND_BAD_REQUEST", "브랜드 입력이 올바르지 않습니다."),
}
