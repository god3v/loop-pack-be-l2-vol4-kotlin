package com.loopers.interfaces.api

import com.loopers.support.error.ErrorStatus
import org.springframework.http.HttpStatus

/**
 * 도메인 [ErrorStatus] 를 HTTP 상태로 변환하는 유일한 지점.
 * 도메인/애플리케이션은 HttpStatus 를 모르고, 이 매퍼만 프레임워크를 안다.
 */
fun ErrorStatus.toHttpStatus(): HttpStatus = when (this) {
    ErrorStatus.BAD_REQUEST -> HttpStatus.BAD_REQUEST
    ErrorStatus.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
    ErrorStatus.FORBIDDEN -> HttpStatus.FORBIDDEN
    ErrorStatus.NOT_FOUND -> HttpStatus.NOT_FOUND
    ErrorStatus.CONFLICT -> HttpStatus.CONFLICT
    ErrorStatus.INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR
}
