package com.loopers.domain.product

import com.loopers.support.error.ErrorType
import org.springframework.http.HttpStatus

enum class ProductErrorType(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    PRODUCT_BAD_REQUEST(HttpStatus.BAD_REQUEST, "PRODUCT_BAD_REQUEST", "상품 입력이 올바르지 않습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "재고가 부족합니다."),
}
