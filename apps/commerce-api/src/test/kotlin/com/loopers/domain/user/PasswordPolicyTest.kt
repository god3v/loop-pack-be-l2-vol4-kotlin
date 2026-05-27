package com.loopers.domain.user

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class PasswordPolicyTest {
    private val birthDate: LocalDate = LocalDate.of(2001, 7, 9)
    private val validPlain = "Asdf1234!"

    @DisplayName("유효한 평문이면, 예외가 발생하지 않는다.")
    @Test
    fun doesNotThrow_whenValid() {
        // when / then — 예외 없음
        PasswordPolicy.validate(validPlain, birthDate)
    }

    @DisplayName("길이가 8~16자 범위를 벗어나면, INVALID_PASSWORD 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["Ab1!", "Ab1!Ab1", "Ab1!Ab1!Ab1!Ab1!A", "Ab1!Ab1!Ab1!Ab1!Ab1!"])
    fun throwsInvalidPassword_whenLengthOutOfRange(invalid: String) {
        // when
        val result = assertThrows<CoreException> {
            PasswordPolicy.validate(invalid, birthDate)
        }

        // then
        assertThat(result.errorType).isEqualTo(UserErrorType.INVALID_PASSWORD)
    }

    @DisplayName("허용 문자 집합 외 문자가 포함되면, INVALID_PASSWORD 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["Asdf1234한", "Asdf 1234!", "한a1!@#\$%"])
    fun throwsInvalidPassword_whenContainsDisallowedChar(invalid: String) {
        // when
        val result = assertThrows<CoreException> {
            PasswordPolicy.validate(invalid, birthDate)
        }

        // then
        assertThat(result.errorType).isEqualTo(UserErrorType.INVALID_PASSWORD)
    }

    @DisplayName("영문/숫자/특수문자 카테고리 중 하나라도 빠지면, INVALID_PASSWORD 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["Asdfasdf", "12345678", "!@#\$%^&*", "Asdf1234", "Asdfasdf!", "12345678!"])
    fun throwsInvalidPassword_whenMissingCategory(invalid: String) {
        // when
        val result = assertThrows<CoreException> {
            PasswordPolicy.validate(invalid, birthDate)
        }

        // then
        assertThat(result.errorType).isEqualTo(UserErrorType.INVALID_PASSWORD)
    }

    @DisplayName("생년월일(yyyyMMdd 또는 yyMMdd) 이 포함되면, INVALID_PASSWORD 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["Ab1!20010709", "20010709Ab!", "Ab!010709AB1"])
    fun throwsInvalidPassword_whenContainsBirthDate(invalid: String) {
        // when
        val result = assertThrows<CoreException> {
            PasswordPolicy.validate(invalid, birthDate)
        }

        // then
        assertThat(result.errorType).isEqualTo(UserErrorType.INVALID_PASSWORD)
    }
}
