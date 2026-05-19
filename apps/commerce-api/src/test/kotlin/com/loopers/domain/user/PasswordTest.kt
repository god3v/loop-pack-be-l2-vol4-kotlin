package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PasswordTest {
    @DisplayName("같은 value 를 가진 두 Password 는 equals 이며 동일한 hashCode 를 가진다.")
    @Test
    fun equalsAndHashCode_whenSameValue() {
        // give
        val a = Password("anyHashValue")
        val b = Password("anyHashValue")

        // then
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @DisplayName("다른 value 를 가진 두 Password 는 equals 가 아니다.")
    @Test
    fun notEquals_whenDifferentValue() {
        // give
        val a = Password("hashA")
        val b = Password("hashB")

        // then
        assertThat(a).isNotEqualTo(b)
    }

    @DisplayName("toString 은 value 를 노출하지 않고 마스킹된 표현을 반환한다.")
    @Test
    fun toString_returnsMaskedValue() {
        // give
        val password = Password("sensitiveHashValue123")

        // when
        val str = password.toString()

        // then
        assertThat(str).isEqualTo("Password(****)")
        assertThat(str).doesNotContain("sensitiveHashValue123")
    }
}
