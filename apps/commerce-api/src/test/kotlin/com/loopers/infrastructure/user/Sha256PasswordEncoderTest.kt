package com.loopers.infrastructure.user

import com.loopers.domain.user.PasswordEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class Sha256PasswordEncoderTest {
    private val encoder: PasswordEncoder = Sha256PasswordEncoder()

    @DisplayName("encode 한 값은 원본 평문과 다르며, matches 호출 시 true 를 반환한다.")
    @Test
    fun returnsHashedValue_andMatchesOriginal() {
        // give
        val raw = "Asdf1234!"

        // when
        val encoded = encoder.encode(raw)

        // then
        assertThat(encoded).isNotEqualTo(raw)
        assertThat(encoder.matches(raw, encoded)).isTrue()
    }

    @DisplayName("SHA-256 은 deterministic 하므로, 같은 평문은 항상 같은 hash 를 생성한다.")
    @Test
    fun returnsSameHash_whenSamePlain() {
        // give
        val raw = "Asdf1234!"

        // when
        val first = encoder.encode(raw)
        val second = encoder.encode(raw)

        // then
        assertThat(first).isEqualTo(second)
    }

    @DisplayName("다른 평문이 주어지면, matches 는 false 를 반환한다.")
    @Test
    fun returnsFalse_whenPlainDoesNotMatch() {
        // give
        val raw = "Asdf1234!"
        val encoded = encoder.encode(raw)

        // when
        val result = encoder.matches("Wrong1234!", encoded)

        // then
        assertThat(result).isFalse()
    }
}
