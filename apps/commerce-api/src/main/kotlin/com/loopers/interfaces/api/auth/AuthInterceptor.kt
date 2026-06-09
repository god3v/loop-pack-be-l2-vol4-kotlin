package com.loopers.interfaces.api.auth

import com.loopers.application.user.UserFacade
import com.loopers.domain.user.UserErrorType
import com.loopers.support.error.CoreException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthInterceptor(
    private val userFacade: UserFacade,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true
        if (!handler.hasMethodAnnotation(RequireAuth::class.java)) return true

        val loginId = request.getHeader(HEADER_LOGIN_ID)
        val loginPw = request.getHeader(HEADER_LOGIN_PW)
        if (loginId.isNullOrBlank() || loginPw.isNullOrBlank()) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        val user = userFacade.authenticate(loginId, loginPw)
        request.setAttribute(ATTRIBUTE_AUTH_USER, AuthUser(id = user.id, loginId = user.loginId))
        return true
    }

    companion object {
        const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
        const val ATTRIBUTE_AUTH_USER = "AUTHENTICATED_USER"
    }
}
