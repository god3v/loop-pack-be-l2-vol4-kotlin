package com.loopers.interfaces.api

import com.loopers.domain.user.UserFixture
import com.loopers.infrastructure.user.UserJpaRepository
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
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_SIGNUP = "/api/v1/users"
        private const val ENDPOINT_ME = "/api/v1/users/me"
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
        @DisplayName("유효한 요청 본문으로 가입을 호출하면, 200 + ApiResponse.success({id, loginId}) 응답을 반환한다.")
        @Test
        fun returnsSuccessWithIdAndLoginId_whenValidRequest() {
            // give
            val request = validSignupRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}

            // when
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(request), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
            )
        }

        @DisplayName("형식이 잘못된 입력으로 가입을 호출하면, 400 SIGNUP_BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenInputIsInvalid() {
            // give
            val request = validSignupRequest(loginId = "한글포함")
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}

            // when
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(request), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("SIGNUP_BAD_REQUEST") },
            )
        }

        @DisplayName("이미 가입된 loginId 로 가입을 호출하면, 409 DUPLICATE_LOGIN_ID 응답을 반환한다.")
        @Test
        fun returnsConflict_whenLoginIdAlreadyExists() {
            // give
            val first = validSignupRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}
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

        @DisplayName("이미 가입된 email 로 가입을 호출하면, 409 DUPLICATE_EMAIL 응답을 반환한다.")
        @Test
        fun returnsConflict_whenEmailAlreadyExists() {
            // give
            val first = validSignupRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}
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

        @DisplayName("유효한 요청 본문으로 가입을 호출하면, 응답에 password 필드/평문 값이 노출되지 않는다.")
        @Test
        fun doesNotExposePassword_inResponse() {
            // give
            val request = validSignupRequest()

            // when
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(request), String::class.java)

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body).doesNotContain("password") },
                { assertThat(response.body).doesNotContain(UserFixture.DEFAULT_PASSWORD) },
            )
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class GetMyInfo {
        @DisplayName("정상 헤더로 내 정보를 조회하면, 200 + 마스킹된 name 을 포함한 응답을 반환한다.")
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

        @DisplayName("인증 헤더(X-Loopers-LoginId, X-Loopers-LoginPw) 없이 내 정보를 조회하면, 401 UNAUTHORIZED 응답을 반환한다.")
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

        @DisplayName("잘못된 비밀번호 헤더로 내 정보를 조회하면, 401 UNAUTHORIZED 응답을 반환한다.")
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

        @DisplayName("정상 헤더로 내 정보를 조회하면, 응답 본문에 password 필드/평문 값이 노출되지 않는다.")
        @Test
        fun doesNotExposePassword_inResponse() {
            // give
            signUpDefaultUser()
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
                set(HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD)
            }

            // when
            val response = testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, HttpEntity<Unit>(headers), String::class.java)

            // then
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body).doesNotContain("password") },
                { assertThat(response.body).doesNotContain(UserFixture.DEFAULT_PASSWORD) },
            )
        }
    }

    private fun signUpDefaultUser() {
        val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}
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
}
