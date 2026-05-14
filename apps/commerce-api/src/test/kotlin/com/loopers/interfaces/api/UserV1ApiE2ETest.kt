package com.loopers.interfaces.api

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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT_SIGNUP = "/api/v1/users"

        private fun validSignupRequest(
            loginId: String = "goryeojin",
            password: String = "Asdf1234!",
            name: String = "고려진",
            birthDate: LocalDate = LocalDate.of(2001, 7, 9),
            email: String = "goryeojin@example.com",
        ): UserV1Dto.SignupRequest = UserV1Dto.SignupRequest(loginId, password, name, birthDate, email)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class SignUp {
        @DisplayName("정상 가입 요청이면, 200 + ApiResponse.success({id, loginId}) 응답을 반환한다.")
        @Test
        fun returnsSuccessWithIdAndLoginId_whenValidRequest() {
            // arrange
            val request = validSignupRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}

            // act
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(request), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.loginId).isEqualTo("goryeojin") },
            )
        }

        @DisplayName("형식이 잘못된 입력이면, 400 SIGNUP_BAD_REQUEST 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenInputIsInvalid() {
            // arrange
            val request = validSignupRequest(loginId = "한글포함")
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}

            // act
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(request), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("SIGNUP_BAD_REQUEST") },
            )
        }

        @DisplayName("중복된 loginId 로 가입 요청 시, 409 DUPLICATE_LOGIN_ID 응답을 반환한다.")
        @Test
        fun returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            val first = validSignupRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(first), responseType)

            val duplicate = validSignupRequest(email = "other@example.com")

            // act
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(duplicate), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("DUPLICATE_LOGIN_ID") },
            )
        }

        @DisplayName("중복된 email 로 가입 요청 시, 409 DUPLICATE_EMAIL 응답을 반환한다.")
        @Test
        fun returnsConflict_whenEmailAlreadyExists() {
            // arrange
            val first = validSignupRequest()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(first), responseType)

            val duplicate = validSignupRequest(loginId = "anothername")

            // act
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(duplicate), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("DUPLICATE_EMAIL") },
            )
        }

        @DisplayName("가입 성공 응답에는 password 가 포함되지 않는다.")
        @Test
        fun doesNotExposePassword_inResponse() {
            // arrange
            val request = validSignupRequest()

            // act
            val response = testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, HttpEntity(request), String::class.java)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body).doesNotContain("password") },
                { assertThat(response.body).doesNotContain("Asdf1234!") },
            )
        }
    }
}
