package com.loopers.domain.user

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class EmailTest {
    @DisplayName("Email 을 생성할 때, ")
    @Nested
    inner class Of {
        @DisplayName("유효한 형식으로 생성하면, 동일한 value 를 가진 Email 이 반환된다.")
        @ParameterizedTest
        @ValueSource(
            strings = [
                "user@example.com",
                "a@b.co",
                "user.name+tag@example.com",
                "user-1@sub.example.org",
            ],
        )
        fun createsEmail_whenValidFormat(validEmail: String) {
            // when
            val email = Email.of(validEmail)

            // then
            assertThat(email.value).isEqualTo(validEmail)
        }

        @DisplayName("형식이 잘못된 입력으로 생성하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.EmailTest#invalidEmails")
        fun throwsSignupBadRequest_whenInvalidFormat(invalidEmail: String) {
            // when
            val result = assertThrows<CoreException> {
                Email.of(invalidEmail)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }

        @DisplayName("길이가 255자를 초과하는 email 로 생성하면, SIGNUP_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsSignupBadRequest_whenExceeds255Chars() {
            // give
            val tooLong = "a".repeat(250) + "@example.com"

            // when
            val result = assertThrows<CoreException> {
                Email.of(tooLong)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST)
        }
    }

    @DisplayName("Email 동등성을 비교할 때, ")
    @Nested
    inner class Equality {
        @DisplayName("같은 value 를 가진 두 Email 은 동등하며, 동일한 해시코드를 가진다.")
        @Test
        fun equalsAndHashCode_whenSameValue() {
            // give
            val a = Email.of("user@example.com")
            val b = Email.of("user@example.com")

            // then
            assertThat(a).isEqualTo(b)
            assertThat(a.hashCode()).isEqualTo(b.hashCode())
        }

        @DisplayName("다른 value 를 가진 두 Email 은 동등하지 않다.")
        @Test
        fun notEquals_whenDifferentValue() {
            // give
            val a = Email.of("a@example.com")
            val b = Email.of("b@example.com")

            // then
            assertThat(a).isNotEqualTo(b)
        }
    }

    @DisplayName("Email 을 문자열로 변환하면, value 값을 그대로 반환한다.")
    @Test
    fun toString_returnsValue() {
        // give
        val email = Email.of("user@example.com")

        // when
        val str = email.toString()

        // then
        assertThat(str).isEqualTo("user@example.com")
    }

    companion object {
        @JvmStatic
        fun invalidEmails(): List<String> = listOf(
            "noatmark.com",
            "@example.com",
            "a@b",
            "a@b.",
            "user@",
        )
    }
}
