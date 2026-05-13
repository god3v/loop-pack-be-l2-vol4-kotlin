package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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
}
