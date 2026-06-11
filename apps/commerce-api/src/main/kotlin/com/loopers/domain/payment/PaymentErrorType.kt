package com.loopers.domain.payment

import com.loopers.support.error.ErrorStatus
import com.loopers.support.error.ErrorType

enum class PaymentErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    INVALID_PAYMENT_TRANSITION(ErrorStatus.CONFLICT, "INVALID_PAYMENT_TRANSITION", "허용되지 않는 결제 상태 전이입니다."),
    PAYMENT_NOT_FOUND(ErrorStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."),
}
