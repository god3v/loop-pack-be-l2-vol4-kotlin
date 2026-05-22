package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PasswordEncryptionUtilTest {
    @DisplayName("encode 한 결과는 원본 평문과 다르며, SHA-256 hex (64자) 형식이다.")
    @Test
    fun returnsHashedHexValue() {
        // give
        val raw = "Asdf1234!"

        // when
        val encoded = PasswordEncryptionUtil.encode(raw)

        // then
        assertThat(encoded).isNotEqualTo(raw)
        assertThat(encoded).hasSize(64)
        assertThat(encoded).matches("^[0-9a-f]+$")
    }

    @DisplayName("같은 평문은 항상 같은 hash 를 반환한다 (deterministic).")
    @Test
    fun returnsSameHash_whenSamePlain() {
        // when
        val first = PasswordEncryptionUtil.encode("Asdf1234!")
        val second = PasswordEncryptionUtil.encode("Asdf1234!")

        // then
        assertThat(first).isEqualTo(second)
    }

    @DisplayName("다른 평문은 다른 hash 를 반환한다.")
    @Test
    fun returnsDifferentHash_whenDifferentPlain() {
        // when
        val first = PasswordEncryptionUtil.encode("Asdf1234!")
        val second = PasswordEncryptionUtil.encode("Wrong1234!")

        // then
        assertThat(first).isNotEqualTo(second)
    }
}
