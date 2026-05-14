package com.loopers.interfaces.api

import com.loopers.domain.user.UserFixture
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_SIGNUP = "/api/v1/users"
        private const val ENDPOINT_ME = "/api/v1/users/me"
        private const val ENDPOINT_PASSWORD = "/api/v1/users/me/password"
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"

        private fun validSignupRequest(
            loginId: String = UserFixture.DEFAULT_LOGIN_ID,
            password: String = UserFixture.DEFAULT_PASSWORD,
            name: String = UserFixture.DEFAULT_NAME,
            birthDate: LocalDate = UserFixture.DEFAULT_BIRTH_DATE,
            email: String = UserFixture.DEFAULT_EMAIL,
        ): UserV1Dto.SignupRequest = UserV1Dto.SignupRequest(loginId, password, name, birthDate, email)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 정보로 회원가입하면, data 가 없는 success 응답을 받는다.")
        @Test
        fun returnsSuccess_whenValidRequest() {
            // give
            val request = validSignupRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // when
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(request), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).isNull() },
            )
        }

        @DisplayName("형식이 잘못된 입력으로 회원가입하면, 400 SIGNUP_BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInputIsInvalid() {
            // give
            val request = validSignupRequest(loginId = "한글포함")
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // when
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(request), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("SIGNUP_BAD_REQUEST") },
            )
        }

        @DisplayName("이미 가입된 아이디로 회원가입하면, 409 DUPLICATE_LOGIN_ID 응답을 받는다.")
        @Test
        fun returnsConflict_whenLoginIdAlreadyExists() {
            // give
            val first = validSignupRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(first), responseType)
            val duplicate = validSignupRequest(email = "other@example.com")

            // when
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(duplicate), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("DUPLICATE_LOGIN_ID") },
            )
        }

        @DisplayName("이미 가입된 이메일로 회원가입하면, 409 DUPLICATE_EMAIL 응답을 받는다.")
        @Test
        fun returnsConflict_whenEmailAlreadyExists() {
            // give
            val first = validSignupRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(first), responseType)
            val duplicate = validSignupRequest(loginId = "anothername")

            // when
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(duplicate), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("DUPLICATE_EMAIL") },
            )
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class GetMyInfo {
        @DisplayName("유효한 헤더로 내 정보를 조회하면, 성공 응답을 받는다.")
        @Test
        fun returnsMaskedMyInfo_whenValidHeaders() {
            // give
            signUpDefaultUser()

            // when
            val response = exchangeGetMe(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_PASSWORD)

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
                { assertThat(response.body?.data?.name).isEqualTo("고려*") },
                { assertThat(response.body?.data?.email).isEqualTo(UserFixture.DEFAULT_EMAIL) },
                { assertThat(response.body?.data?.birthDate).isEqualTo(UserFixture.DEFAULT_BIRTH_DATE) },
            )
        }

        @DisplayName("인증 헤더 없이 내 정보를 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenHeadersAreMissing() {
            // give
            signUpDefaultUser()

            // when
            val response = exchangeGetMe(loginId = null, password = null)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNAUTHORIZED") },
            )
        }

        @DisplayName("잘못된 비밀번호 헤더로 내 정보를 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenPasswordMismatch() {
            // give
            signUpDefaultUser()

            // when
            val response = exchangeGetMe(UserFixture.DEFAULT_LOGIN_ID, "Wrong1234!")

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNAUTHORIZED") },
            )
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    inner class ChangePassword {
        private val nextPw = "NewPw5678!"

        @DisplayName("정상 이중 인증과 RULE 을 통과한 새 비밀번호로 변경하면, 200 응답을 반환하고 새 비밀번호로 GET /me 인증이 가능하다.")
        @Test
        fun returnsSuccessAndAllowsAuthenticationWithnextPw_whenValidRequest() {
            // give
            signUpDefaultUser()

            // when
            val response = exchangePatchPassword(
                loginId = UserFixture.DEFAULT_LOGIN_ID,
                loginPw = UserFixture.DEFAULT_PASSWORD,
                body = UserV1Dto.ChangePasswordRequest(
                    prevPw = UserFixture.DEFAULT_PASSWORD,
                    nextPw = nextPw,
                ),
            )
            val afterMe = exchangeGetMe(UserFixture.DEFAULT_LOGIN_ID, nextPw)

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(afterMe.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(afterMe.body?.data?.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
            )
        }

        @DisplayName("헤더의 비밀번호가 일치하지 않으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        fun returnsUnauthorized_whenloginPwMismatch() {
            // give
            signUpDefaultUser()

            // when
            val response = exchangePatchPassword(
                loginId = UserFixture.DEFAULT_LOGIN_ID,
                loginPw = "Wrong1234!",
                body = UserV1Dto.ChangePasswordRequest(
                    prevPw = UserFixture.DEFAULT_PASSWORD,
                    nextPw = nextPw,
                ),
            )

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNAUTHORIZED") },
            )
        }

        @DisplayName("헤더는 일치하지만 body 의 prevPw 가 일치하지 않으면, 401 UNAUTHORIZED 응답을 반환한다.")
        @Test
        fun returnsUnauthorized_whenBodyprevPwMismatch() {
            // give
            signUpDefaultUser()

            // when
            val response = exchangePatchPassword(
                loginId = UserFixture.DEFAULT_LOGIN_ID,
                loginPw = UserFixture.DEFAULT_PASSWORD,
                body = UserV1Dto.ChangePasswordRequest(
                    prevPw = "Wrong1234!",
                    nextPw = nextPw,
                ),
            )

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNAUTHORIZED") },
            )
        }

        @DisplayName("새 비밀번호가 RULE 을 위반하면, 400 INVALID_PASSWORD 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whennextPwViolatesRule() {
            // give
            signUpDefaultUser()

            // when
            val response = exchangePatchPassword(
                loginId = UserFixture.DEFAULT_LOGIN_ID,
                loginPw = UserFixture.DEFAULT_PASSWORD,
                body = UserV1Dto.ChangePasswordRequest(
                    prevPw = UserFixture.DEFAULT_PASSWORD,
                    nextPw = "short1!",
                ),
            )

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("INVALID_PASSWORD") },
            )
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, 400 PASSWORD_CHANGE_BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whennextPwEqualsCurrent() {
            // give
            signUpDefaultUser()

            // when
            val response = exchangePatchPassword(
                loginId = UserFixture.DEFAULT_LOGIN_ID,
                loginPw = UserFixture.DEFAULT_PASSWORD,
                body = UserV1Dto.ChangePasswordRequest(
                    prevPw = UserFixture.DEFAULT_PASSWORD,
                    nextPw = UserFixture.DEFAULT_PASSWORD,
                ),
            )

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PASSWORD_CHANGE_BAD_REQUEST") },
            )
        }
    }

    private fun signUpDefaultUser() {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(validSignupRequest()), responseType)
    }

    private fun exchangeGetMe(
        loginId: String?,
        password: String?,
    ): ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> {
        val headers = HttpHeaders().apply {
            if (loginId != null) set(HEADER_LOGIN_ID, loginId)
            if (password != null) set(HEADER_LOGIN_PW, password)
        }
        val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>>() {}
        return testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, HttpEntity<Unit>(headers), responseType)
    }

    private fun exchangePatchPassword(
        loginId: String?,
        loginPw: String?,
        body: UserV1Dto.ChangePasswordRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val headers = HttpHeaders().apply {
            if (loginId != null) set(HEADER_LOGIN_ID, loginId)
            if (loginPw != null) set(HEADER_LOGIN_PW, loginPw)
        }
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(ENDPOINT_PASSWORD, HttpMethod.PATCH, HttpEntity(body, headers), responseType)
    }
}
