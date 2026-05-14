package com.loopers.domain.user

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class PasswordTest {
    private val birthDate: LocalDate = LocalDate.of(2001, 7, 9)
    private val validPlain = "Asdf1234!"

    @DisplayName("Password 를 생성할 때, ")
    @Nested
    inner class Of {
        @DisplayName("유효한 형식으로 생성하면, 동일한 value 를 가진 Password 가 반환된다.")
        @Test
        fun createsPassword_whenValid() {
            // when
            val password = Password.of(validPlain, birthDate)

            // then
            assertThat(password.value).isEqualTo(validPlain)
        }

        @DisplayName("길이가 8~16자 범위를 벗어나면, INVALID_PASSWORD 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Ab1!", "Ab1!Ab1", "Ab1!Ab1!Ab1!Ab1!A", "Ab1!Ab1!Ab1!Ab1!Ab1!"])
        fun throwsInvalidPassword_whenLengthOutOfRange(invalid: String) {
            // when
            val result = assertThrows<CoreException> {
                Password.of(invalid, birthDate)
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
                Password.of(invalid, birthDate)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.INVALID_PASSWORD)
        }

        @DisplayName("영문/숫자/특수문자 카테고리 중 하나라도 빠지면, INVALID_PASSWORD 예외가 발생한다.")
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
        fun throwsInvalidPassword_whenMissingCategory(invalid: String) {
            // when
            val result = assertThrows<CoreException> {
                Password.of(invalid, birthDate)
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
                Password.of(invalid, birthDate)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.INVALID_PASSWORD)
        }
    }

    @DisplayName("Password.matches 를 호출할 때, ")
    @Nested
    inner class Matches {
        @DisplayName("동일한 평문이 주어지면, true 를 반환한다.")
        @Test
        fun returnsTrue_whenSamePlaintext() {
            // give
            val password = Password.of(validPlain, birthDate)

            // when
            val result = password.matches(validPlain)

            // then
            assertThat(result).isTrue()
        }

        @DisplayName("다른 평문이 주어지면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenDifferentPlaintext() {
            // give
            val password = Password.of(validPlain, birthDate)

            // when
            val result = password.matches("Wrong1234!")

            // then
            assertThat(result).isFalse()
        }
    }

    @DisplayName("Password 동등성을 비교할 때, ")
    @Nested
    inner class Equality {
        @DisplayName("같은 value 를 가진 두 Password 는 동등하며, 동일한 해시코드를 가진다.")
        @Test
        fun equalsAndHashCode_whenSameValue() {
            // give
            val a = Password.of(validPlain, birthDate)
            val b = Password.of(validPlain, birthDate)

            // then
            assertThat(a).isEqualTo(b)
            assertThat(a.hashCode()).isEqualTo(b.hashCode())
        }
    }

    @DisplayName("Password 를 문자열로 변환하면, value 를 노출하지 않는 마스킹된 표현을 반환한다.")
    @Test
    fun toString_returnsMaskedValue() {
        // give
        val password = Password.of(validPlain, birthDate)

        // when
        val str = password.toString()

        // then
        assertThat(str).isEqualTo("Password(****)")
        assertThat(str).doesNotContain(validPlain)
    }
}
