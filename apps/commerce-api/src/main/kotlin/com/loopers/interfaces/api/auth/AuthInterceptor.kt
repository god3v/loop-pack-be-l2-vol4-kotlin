package com.loopers.interfaces.api.auth

import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthInterceptor(
    private val userService: UserService,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true
        if (!handler.hasMethodAnnotation(RequireAuth::class.java)) return true

        val loginId = request.getHeader(HEADER_LOGIN_ID)
        val loginPw = request.getHeader(HEADER_LOGIN_PW)
        if (loginId.isNullOrBlank() || loginPw.isNullOrBlank()) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        userService.authenticate(loginId, loginPw)
        request.setAttribute(ATTRIBUTE_LOGIN_ID, loginId)
        return true
    }

    companion object {
        const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
        const val ATTRIBUTE_LOGIN_ID = "AUTHENTICATED_LOGIN_ID"
    }
}
