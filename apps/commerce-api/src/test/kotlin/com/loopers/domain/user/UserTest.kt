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
        @DisplayName("올바른 정보가 주어지면, 회원이 정상적으로 생성된다.")
        @Test
        fun createsUser_whenAllInputsAreValid() {
            // when
            val user = UserFixture.validUser()

            // then
            assertAll(
                { assertThat(user.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
                { assertThat(user.name).isEqualTo(UserFixture.DEFAULT_NAME) },
                { assertThat(user.birthDate).isEqualTo(UserFixture.DEFAULT_BIRTH_DATE) },
                { assertThat(user.email.value).isEqualTo(UserFixture.DEFAULT_EMAIL) },
            )
        }

        @DisplayName("아이디가 4자 미만이면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenLoginIdIsTooShort() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(loginId = "abc")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("아이디가 20자를 초과하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenLoginIdIsTooLong() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(loginId = "abcdefghijklmnopqrstuv")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("아이디에 한글이 포함되면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenLoginIdContainsKorean() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(loginId = "한글포함")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("아이디에 공백이 포함되면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenLoginIdContainsSpace() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(loginId = "with space")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("아이디에 특수문자가 포함되면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenLoginIdContainsSpecialChar() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(loginId = "spec!")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("이름이 2자 미만이면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenNameIsTooShort() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(name = "김")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("이름이 50자를 초과하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenNameIsTooLong() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(name = "a".repeat(51))
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("이름에 숫자가 포함되면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenNameContainsDigit() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(name = "김수1")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("이름에 공백이 포함되면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenNameContainsSpace() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(name = "김 수")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("이름에 특수문자가 포함되면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenNameContainsSpecialChar() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(name = "김수!")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("만 14세 미만이면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenBirthDateIsUnder14() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(birthDate = LocalDate.now().minusYears(13))
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("유효하지 않은 이메일을 입력하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenEmailIsInvalid() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(email = "noatmark.com")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    inner class ChangePassword {
        @DisplayName("새 Password 를 전달하면, User.password 가 새 인스턴스로 갱신된다.")
        @Test
        fun updatesPassword_whenInvoked() {
            // give
            val user = UserFixture.validUser()
            val previousPassword = user.password
            val newPassword = Password("newHashValue123")

            // when
            user.changePassword(newPassword)

            // then
            assertAll(
                { assertThat(user.password).isEqualTo(newPassword) },
                { assertThat(user.password).isNotEqualTo(previousPassword) },
            )
        }
    }
}
