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
            // when
            val user = UserFixture.validUser()

            // then
            assertAll(
                { assertThat(user.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
                { assertThat(user.name).isEqualTo(UserFixture.DEFAULT_NAME) },
                { assertThat(user.birthDate).isEqualTo(UserFixture.DEFAULT_BIRTH_DATE) },
                { assertThat(user.email).isEqualTo(UserFixture.DEFAULT_EMAIL) },
            )
        }

        @DisplayName("잘못된 loginId 로 회원을 생성하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenLoginIdIsInvalid() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(loginId = "한글포함")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("2자 미만의 name 으로 회원을 생성하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenNameIsTooShort() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(name = "김")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("형식이 잘못된 email 로 회원을 생성하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenEmailIsInvalid() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(email = "noatmark.com")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("만 14세 미만의 birthDate 로 회원을 생성하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenBirthDateIsUnderAge14() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(birthDate = LocalDate.now().minusYears(13))
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("미래 birthDate 로 회원을 생성하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenBirthDateIsFuture() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(birthDate = LocalDate.now().plusDays(1))
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("영문/숫자/특수문자 3 카테고리 중 하나라도 빠진 비밀번호로 회원을 생성하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenPasswordMissingCategory() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(password = "asdfasdf")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("생년월일(yyyyMMdd) 이 포함된 비밀번호로 회원을 생성하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenPasswordContainsBirthDate() {
            // when
            val result = assertThrows<CoreException> {
                UserFixture.validUser(password = "Ab1!20010709")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }
    }
}
