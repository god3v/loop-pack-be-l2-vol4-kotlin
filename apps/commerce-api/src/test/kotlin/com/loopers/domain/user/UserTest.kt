package com.loopers.domain.user

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserTest {
    @DisplayName("회원을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 입력이 모두 주어지면, 회원이 정상적으로 생성된다.")
        @Test
        fun createsUser_whenAllInputsAreValid() {
            // give
            val loginId = "goryeojin"
            val password = "Asdf1234!"
            val name = "고려진"
            val birthDate = LocalDate.of(2001, 7, 9)
            val email = "goryeojin@example.com"

            // when
            val user = User.signUp(
                loginId = loginId,
                password = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )

            // then
            assertAll(
                { assertThat(user.loginId).isEqualTo(loginId) },
                { assertThat(user.name).isEqualTo(name) },
                { assertThat(user.birthDate).isEqualTo(birthDate) },
                { assertThat(user.email).isEqualTo(email) },
            )
        }
    }

    @DisplayName("회원 생성 시 invariant 가 위반되면, ")
    @Nested
    inner class InvariantViolation {
        @DisplayName("loginId 형식이 잘못되면 SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenLoginIdIsInvalid() {
            val result = assertThrows<CoreException> {
                User.signUp("한글포함", "Asdf1234!", "고려진", LocalDate.of(2001, 7, 9), "goryeojin@example.com")
            }
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("name 이 2자 미만이면 SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenNameIsTooShort() {
            val result = assertThrows<CoreException> {
                User.signUp("goryeojin", "Asdf1234!", "김", LocalDate.of(2001, 7, 9), "goryeojin@example.com")
            }
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("email 형식이 잘못되면 SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenEmailIsInvalid() {
            val result = assertThrows<CoreException> {
                User.signUp("goryeojin", "Asdf1234!", "고려진", LocalDate.of(2001, 7, 9), "noatmark.com")
            }
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("만 14세 미만의 birthDate 면 SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenBirthDateIsUnderAge14() {
            val underAge = LocalDate.now().minusYears(13)
            val result = assertThrows<CoreException> {
                User.signUp("goryeojin", "Asdf1234!", "고려진", underAge, "goryeojin@example.com")
            }
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("미래 birthDate 면 SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenBirthDateIsFuture() {
            val future = LocalDate.now().plusDays(1)
            val result = assertThrows<CoreException> {
                User.signUp("goryeojin", "Asdf1234!", "고려진", future, "goryeojin@example.com")
            }
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("비밀번호가 3 카테고리(영문/숫자/특수)를 모두 만족하지 않으면 SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenPasswordMissingCategory() {
            val result = assertThrows<CoreException> {
                User.signUp("goryeojin", "asdfasdf", "고려진", LocalDate.of(2001, 7, 9), "goryeojin@example.com")
            }
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd) 이 포함되면 SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenPasswordContainsBirthDate() {
            val result = assertThrows<CoreException> {
                User.signUp("goryeojin", "Ab1!20010709", "고려진", LocalDate.of(2001, 7, 9), "goryeojin@example.com")
            }
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }
    }
}
