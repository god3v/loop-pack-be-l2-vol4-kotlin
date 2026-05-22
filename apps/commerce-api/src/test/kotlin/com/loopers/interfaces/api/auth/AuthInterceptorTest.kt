package com.loopers.interfaces.api.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.user.User
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.ApiControllerAdvice
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class AuthInterceptorTest {
    private val userService: UserService = mockk()
    private val interceptor = AuthInterceptor(userService)
    private val argumentResolver = LoginUserArgumentResolver()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(StubController())
        .addInterceptors(interceptor)
        .setCustomArgumentResolvers(argumentResolver)
        .setControllerAdvice(ApiControllerAdvice())
        .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
        .build()

    @RestController
    class StubController {
        @GetMapping("/test/protected")
        @RequireAuth
        fun protectedEndpoint(@LoginUser loginId: String): String = "ok:$loginId"

        @GetMapping("/test/public")
        fun publicEndpoint(): String = "public"
    }

    @DisplayName("@RequireAuth 가 없는 핸들러에 대해, ")
    @Nested
    inner class WithoutRequireAuth {
        @DisplayName("헤더 없이 호출해도 통과한다.")
        @Test
        fun passesThrough_whenAnnotationAbsent() {
            // when / then
            mockMvc.perform(get("/test/public"))
                .andExpect(status().isOk)
                .andExpect(content().string("\"public\""))

            verify(exactly = 0) { userService.authenticate(any(), any()) }
        }
    }

    @DisplayName("@RequireAuth 핸들러에 대해, ")
    @Nested
    inner class WithRequireAuth {
        @DisplayName("loginId 헤더가 누락되면, 401 UNAUTHORIZED 응답이 반환되고 인증은 호출되지 않는다.")
        @Test
        fun returnsUnauthorized_whenLoginIdHeaderMissing() {
            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))

            verify(exactly = 0) { userService.authenticate(any(), any()) }
        }

        @DisplayName("loginPw 헤더가 누락되면, 401 UNAUTHORIZED 응답이 반환되고 인증은 호출되지 않는다.")
        @Test
        fun returnsUnauthorized_whenLoginPwHeaderMissing() {
            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))

            verify(exactly = 0) { userService.authenticate(any(), any()) }
        }

        @DisplayName("loginId 헤더가 공백 문자열이면, 401 UNAUTHORIZED 응답이 반환된다.")
        @Test
        fun returnsUnauthorized_whenLoginIdHeaderBlank() {
            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, "   ")
                    .header(AuthInterceptor.HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))

            verify(exactly = 0) { userService.authenticate(any(), any()) }
        }

        @DisplayName("UserService.authenticate 가 UNAUTHORIZED 를 던지면, 401 응답이 반환된다.")
        @Test
        fun returnsUnauthorized_whenAuthenticationFails() {
            // give
            every {
                userService.authenticate(UserFixture.DEFAULT_LOGIN_ID, "Wrong1234!")
            } throws CoreException(UserErrorType.UNAUTHORIZED)

            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
                    .header(AuthInterceptor.HEADER_LOGIN_PW, "Wrong1234!"),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))
        }

        @DisplayName("인증에 성공하면, 핸들러로 통과하고 @LoginUser 로 인증된 loginId 가 주입된다.")
        @Test
        fun passesThroughAndInjectsLoginId_whenAuthenticationSucceeds() {
            // give
            every {
                userService.authenticate(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_PASSWORD)
            } returns mockk<User>()

            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
                    .header(AuthInterceptor.HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD),
            )
                .andExpect(status().isOk)
                .andExpect(content().string("\"ok:${UserFixture.DEFAULT_LOGIN_ID}\""))

            verify(exactly = 1) {
                userService.authenticate(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_PASSWORD)
            }
        }
    }
}
