package com.loopers.domain.user

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
            val loginId = "goryeojin"
            val password = "Asdf1234!"
            val name = "고려진"
            val birthDate = LocalDate.of(2001, 7, 9)
            val email = "goryeojin@example.com"
            every { userRepository.findByLoginId(any()) } returns null
            every { userRepository.findByEmail(any()) } returns null
            every { userRepository.save(any()) } answers { firstArg() }

            // when
            val saved = userService.signup(loginId, password, name, birthDate, email)

            // then
            assertAll(
                { assertThat(saved.loginId).isEqualTo(loginId) },
                { verify(exactly = 1) { userRepository.save(any()) } },
            )
        }

        @DisplayName("동일한 loginId 가 이미 존재하면, DUPLICATE_LOGIN_ID 예외가 발생한다.")
        @Test
        fun throwsDuplicateLoginIdException_whenLoginIdAlreadyExists() {
            // give
            val loginId = "goryeojin"
            every { userRepository.findByLoginId(loginId) } returns mockk<User>()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(
                    loginId = loginId,
                    password = "Asdf1234!",
                    name = "고려진",
                    birthDate = LocalDate.of(2001, 7, 9),
                    email = "goryeojin@example.com",
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID)
        }

        @DisplayName("동일한 email 이 이미 존재하면, DUPLICATE_EMAIL 예외가 발생한다.")
        @Test
        fun throwsDuplicateEmailException_whenEmailAlreadyExists() {
            // give
            val email = "goryeojin@example.com"
            every { userRepository.findByLoginId(any()) } returns null
            every { userRepository.findByEmail(email) } returns mockk<User>()

            // when
            val result = assertThrows<CoreException> {
                userService.signup(
                    loginId = "goryeojin",
                    password = "Asdf1234!",
                    name = "고려진",
                    birthDate = LocalDate.of(2001, 7, 9),
                    email = email,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_EMAIL)
        }

        @DisplayName("형식이 잘못된 loginId 로 가입을 시도하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["abc", "abcdefghijklmnopqrstuv", "한글포함", "with space", "spec!"])
        fun throwsSignupBadRequest_whenLoginIdIsInvalid(invalidLoginId: String) {
            // give
            every { userRepository.findByLoginId(any()) } returns null
            every { userRepository.findByEmail(any()) } returns null

            // when
            val result = assertThrows<CoreException> {
                userService.signup(invalidLoginId, "Asdf1234!", "고려진", LocalDate.of(2001, 7, 9), "goryeojin@example.com")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("형식이 잘못된 name 으로 가입을 시도하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.UserServiceTest#invalidNames")
        fun throwsSignupBadRequest_whenNameIsInvalid(invalidName: String) {
            // give
            every { userRepository.findByLoginId(any()) } returns null
            every { userRepository.findByEmail(any()) } returns null

            // when
            val result = assertThrows<CoreException> {
                userService.signup("goryeojin", "Asdf1234!", invalidName, LocalDate.of(2001, 7, 9), "goryeojin@example.com")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("형식이 잘못된 email 로 가입을 시도하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.UserServiceTest#invalidEmails")
        fun throwsSignupBadRequest_whenEmailIsInvalid(invalidEmail: String) {
            // give
            every { userRepository.findByLoginId(any()) } returns null
            every { userRepository.findByEmail(any()) } returns null

            // when
            val result = assertThrows<CoreException> {
                userService.signup("goryeojin", "Asdf1234!", "고려진", LocalDate.of(2001, 7, 9), invalidEmail)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("가입 자격이 없는 birthDate 로 가입을 시도하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.UserServiceTest#invalidBirthDates")
        fun throwsSignupBadRequest_whenBirthDateIsInvalid(invalidBirthDate: LocalDate) {
            // give
            every { userRepository.findByLoginId(any()) } returns null
            every { userRepository.findByEmail(any()) } returns null

            // when
            val result = assertThrows<CoreException> {
                userService.signup("goryeojin", "Asdf1234!", "고려진", invalidBirthDate, "goryeojin@example.com")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
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
