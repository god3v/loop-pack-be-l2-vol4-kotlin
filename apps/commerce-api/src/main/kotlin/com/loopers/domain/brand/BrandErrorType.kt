package com.loopers.domain.brand

import com.loopers.support.error.ErrorType
import org.springframework.http.HttpStatus

enum class BrandErrorType(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    BRAND_BAD_REQUEST(HttpStatus.BAD_REQUEST, "BRAND_BAD_REQUEST", "브랜드 입력이 올바르지 않습니다."),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "브랜드를 찾을 수 없습니다."),
    DUPLICATE_BRAND_NAME(HttpStatus.CONFLICT, "DUPLICATE_BRAND_NAME", "동일한 이름의 브랜드가 이미 존재합니다."),
}
