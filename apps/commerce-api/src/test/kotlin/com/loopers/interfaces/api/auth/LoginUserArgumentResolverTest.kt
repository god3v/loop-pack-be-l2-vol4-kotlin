package com.loopers.interfaces.api.auth

import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest

class LoginUserArgumentResolverTest {
    private val resolver = LoginUserArgumentResolver()

    @DisplayName("supportsParameter 는 @LoginUser 가 붙은 String 파라미터에 대해 true 를 반환한다.")
    @Test
    fun supportsParameter_returnsTrue_whenLoginUserAnnotatedString() {
        // give
        val parameter: MethodParameter = mockk {
            every { hasParameterAnnotation(LoginUser::class.java) } returns true
            every { parameterType } returns String::class.java
        }

        // when
        val result = resolver.supportsParameter(parameter)

        // then
        assertThat(result).isTrue()
    }

    @DisplayName("supportsParameter 는 @LoginUser 가 없는 파라미터에 대해 false 를 반환한다.")
    @Test
    fun supportsParameter_returnsFalse_whenAnnotationAbsent() {
        // give
        val parameter: MethodParameter = mockk {
            every { hasParameterAnnotation(LoginUser::class.java) } returns false
            every { parameterType } returns String::class.java
        }

        // when
        val result = resolver.supportsParameter(parameter)

        // then
        assertThat(result).isFalse()
    }

    @DisplayName("supportsParameter 는 @LoginUser 가 String 이 아닌 타입에 대해 false 를 반환한다.")
    @Test
    fun supportsParameter_returnsFalse_whenParameterTypeIsNotString() {
        // give
        val parameter: MethodParameter = mockk {
            every { hasParameterAnnotation(LoginUser::class.java) } returns true
            every { parameterType } returns Int::class.java
        }

        // when
        val result = resolver.supportsParameter(parameter)

        // then
        assertThat(result).isFalse()
    }

    @DisplayName("resolveArgument 는 request attribute 에 저장된 loginId 를 반환한다.")
    @Test
    fun resolveArgument_returnsLoginIdFromAttribute() {
        // give
        val request: HttpServletRequest = mockk {
            every { getAttribute(AuthInterceptor.ATTRIBUTE_LOGIN_ID) } returns UserFixture.DEFAULT_LOGIN_ID
        }
        val webRequest: NativeWebRequest = mockk {
            every { getNativeRequest(HttpServletRequest::class.java) } returns request
        }

        // when
        val result = resolver.resolveArgument(mockk(), null, webRequest, null)

        // then
        assertThat(result).isEqualTo(UserFixture.DEFAULT_LOGIN_ID)
    }

    @DisplayName("resolveArgument 는 request attribute 가 없으면, UNAUTHORIZED 예외를 던진다.")
    @Test
    fun resolveArgument_throwsUnauthorized_whenAttributeAbsent() {
        // give
        val request: HttpServletRequest = mockk {
            every { getAttribute(AuthInterceptor.ATTRIBUTE_LOGIN_ID) } returns null
        }
        val webRequest: NativeWebRequest = mockk {
            every { getNativeRequest(HttpServletRequest::class.java) } returns request
        }

        // when
        val result = assertThrows<CoreException> {
            resolver.resolveArgument(mockk(), null, webRequest, null)
        }

        // then
        assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
    }
}
