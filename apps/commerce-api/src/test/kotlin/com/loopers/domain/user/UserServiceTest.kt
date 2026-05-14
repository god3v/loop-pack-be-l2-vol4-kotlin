package com.loopers.domain.user

import com.loopers.domain.user.UserFixture.DEFAULT_BIRTH_DATE
import com.loopers.domain.user.UserFixture.DEFAULT_EMAIL
import com.loopers.domain.user.UserFixture.DEFAULT_LOGIN_ID
import com.loopers.domain.user.UserFixture.DEFAULT_NAME
import com.loopers.domain.user.UserFixture.DEFAULT_PASSWORD
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val userService = UserService(userRepository)

    @DisplayName("회원가입을 할 때, ")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 입력이 모두 주어지면, User 가 저장되고 반환된다.")
        @Test
        fun savesAndReturnsUser_whenAllInputsAreValid() {
            // give
            every { userRepository.findByLoginId(any()) } returns null
            every { userRepository.findByEmail(any()) } returns null
            every { userRepository.save(any()) } answers { firstArg() }

            // when
            val saved = userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)

            // then
            assertAll(
                { assertThat(saved.loginId).isEqualTo(DEFAULT_LOGIN_ID) },
                { verify(exactly = 1) { userRepository.save(any()) } },
            )
        }

        @DisplayName("동일한 loginId 가 이미 존재하면, DUPLICATE_LOGIN_ID 예외가 발생한다.")
        @Test
        fun throwsDuplicateLoginIdException_whenLoginIdAlreadyExists() {
            // give
            every { userRepository.findByLoginId(DEFAULT_LOGIN_ID) } returns mockk<User>()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID)
        }

        @DisplayName("동일한 email 이 이미 존재하면, DUPLICATE_EMAIL 예외가 발생한다.")
        @Test
        fun throwsDuplicateEmailException_whenEmailAlreadyExists() {
            // give
            every { userRepository.findByLoginId(any()) } returns null
            every { userRepository.findByEmail(DEFAULT_EMAIL) } returns mockk<User>()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_EMAIL)
        }

        @DisplayName("형식이 잘못된 loginId 로 가입을 시도하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["abc", "abcdefghijklmnopqrstuv", "한글포함", "with space", "spec!"])
        fun throwsSignupBadRequest_whenLoginIdIsInvalid(invalidLoginId: String) {
            // give
            stubNoDuplicate()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(invalidLoginId, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("형식이 잘못된 name 으로 가입을 시도하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.UserServiceTest#invalidNames")
        fun throwsSignupBadRequest_whenNameIsInvalid(invalidName: String) {
            // give
            stubNoDuplicate()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, invalidName, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("형식이 잘못된 email 로 가입을 시도하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.UserServiceTest#invalidEmails")
        fun throwsSignupBadRequest_whenEmailIsInvalid(invalidEmail: String) {
            // give
            stubNoDuplicate()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, invalidEmail)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("가입 자격이 없는 birthDate 로 가입을 시도하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.UserServiceTest#invalidBirthDates")
        fun throwsSignupBadRequest_whenBirthDateIsInvalid(invalidBirthDate: LocalDate) {
            // give
            stubNoDuplicate()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, DEFAULT_NAME, invalidBirthDate, DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("비밀번호 길이가 8~16자 범위를 벗어나면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Ab1!", "Ab1!Ab1", "Ab1!Ab1!Ab1!Ab1!A", "Ab1!Ab1!Ab1!Ab1!Ab1!"])
        fun throwsSignupBadRequest_whenPasswordLengthIsInvalid(invalidPassword: String) {
            // give
            stubNoDuplicate()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, invalidPassword, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("비밀번호에 허용되지 않은 문자가 포함되면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Asdf1234한", "Asdf 1234!", "한a1!@#\$%"])
        fun throwsSignupBadRequest_whenPasswordContainsDisallowedChar(invalidPassword: String) {
            // give
            stubNoDuplicate()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, invalidPassword, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("비밀번호의 3 카테고리(영문/숫자/특수문자) 중 하나라도 빠지면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(
            strings = [
                "Asdfasdf",
                "12345678",
                "!@#\$%^&*",
                "Asdf1234",
                "Asdfasdf!",
                "12345678!",
            ],
        )
        fun throwsSignupBadRequest_whenPasswordMissingCategory(invalidPassword: String) {
            // give
            stubNoDuplicate()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, invalidPassword, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd 또는 yyMMdd)이 포함되면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Ab1!20010709", "20010709Ab!", "Ab!010709AB1"])
        fun throwsSignupBadRequest_whenPasswordContainsBirthDate(invalidPassword: String) {
            // give
            stubNoDuplicate()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, invalidPassword, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        private fun stubNoDuplicate() {
            every { userRepository.findByLoginId(any()) } returns null
            every { userRepository.findByEmail(any()) } returns null
        }
    }

    @DisplayName("로그인할 때, ")
    @Nested
    inner class Authenticate {
        @DisplayName("존재하지 않는 loginId 로 인증을 시도하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginIdNotFound() {
            // give
            every { userRepository.findByLoginId(DEFAULT_LOGIN_ID) } returns null

            // when
            val result = assertThrows<CoreException> {
                userService.authenticate(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }

        @DisplayName("loginId 는 존재하지만 비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordMismatch() {
            // give
            every { userRepository.findByLoginId(DEFAULT_LOGIN_ID) } returns UserFixture.validUser()

            // when
            val result = assertThrows<CoreException> {
                userService.authenticate(DEFAULT_LOGIN_ID, "Wrong1234!")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }

        @DisplayName("loginId 와 비밀번호가 모두 일치하면, 식별된 User 가 반환된다.")
        @Test
        fun returnsUser_whenCredentialsMatch() {
            // give
            val savedUser = UserFixture.validUser()
            every { userRepository.findByLoginId(DEFAULT_LOGIN_ID) } returns savedUser

            // when
            val authenticated = userService.authenticate(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD)

            // then
            assertAll(
                { assertThat(authenticated).isSameAs(savedUser) },
                { assertThat(authenticated.loginId).isEqualTo(DEFAULT_LOGIN_ID) },
            )
        }
    }

    companion object {
        @JvmStatic
        fun invalidNames(): List<String> = listOf(
            "김",
            "a".repeat(51),
            "김수1",
            "김 수",
            "김수!",
        )

        @JvmStatic
        fun invalidEmails(): List<String> = listOf(
            "noatmark.com",
            "@example.com",
            "a@b",
            "a@b.",
            "a".repeat(250) + "@example.com",
        )

        @JvmStatic
        fun invalidBirthDates(): List<LocalDate> = listOf(
            LocalDate.now().plusDays(1),
            LocalDate.now().minusYears(13),
        )
    }
}
