package com.loopers.interfaces.api.auth

/**
 * 인증을 통과한 회원의 식별 정보를 컨트롤러로 전달하는 interfaces 계층 주체(principal) 값 객체.
 *
 * 도메인 `User` 를 그대로 노출하지 않고, 컨트롤러가 필요로 하는 식별자만 좁혀 담는다.
 * `@LoginUser` 파라미터로 주입된다.
 */
data class AuthUser(
    val id: Long,
    val loginId: String,
)
