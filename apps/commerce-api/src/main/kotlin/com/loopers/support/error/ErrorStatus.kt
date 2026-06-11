package com.loopers.support.error

/**
 * 도메인/애플리케이션이 표현하는 프레임워크 무관한 에러 의미 분류.
 * HTTP 상태로의 변환은 interfaces 계층의 매퍼 책임이며, 본 enum 은 프레임워크를 알지 못한다.
 */
enum class ErrorStatus {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR,
}
