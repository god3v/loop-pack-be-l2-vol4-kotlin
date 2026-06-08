package com.loopers.domain.order

import com.loopers.support.error.ErrorType
import org.springframework.http.HttpStatus

enum class OrderErrorType(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    EMPTY_LINES(HttpStatus.BAD_REQUEST, "EMPTY_LINES", "주문에는 1개 이상의 라인이 필요합니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "수량은 1 이상이어야 합니다."),
    LINE_BAD_REQUEST(HttpStatus.BAD_REQUEST, "LINE_BAD_REQUEST", "주문 라인 입력이 올바르지 않습니다."),
    IDEMPOTENCY_KEY_BLANK(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_BLANK", "idempotencyKey 는 비어 있을 수 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "startAt 은 endAt 보다 클 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    ORDER_FORBIDDEN(HttpStatus.FORBIDDEN, "ORDER_FORBIDDEN", "본인의 주문이 아닙니다."),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED", "결제에 실패했습니다."),
}
