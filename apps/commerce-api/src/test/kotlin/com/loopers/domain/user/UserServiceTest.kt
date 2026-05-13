package com.loopers.domain.user

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.LocalDate

class UserServiceTest {
    private val userRepository: UserRepository = mockk()
    private val userService = UserService(userRepository)

    @DisplayName("회원가입을 할 때, ")
    @Nested
    inner class SignUp {
        @DisplayName("유효한 입력이 모두 주어지면, User 가 저장되고 반환된다.")
        @Test
        fun savesAndReturnsUser_whenAllInputsAreValid() {
            // give
            val loginId = "goryeojin"
            val password = "Asdf1234!"
            val name = "고려진"
            val birthDate = LocalDate.of(2001, 7, 9)
            val email = "goryeojin@example.com"
            every { userRepository.save(any()) } answers { firstArg() }

            // when
            val saved = userService.signup(loginId, password, name, birthDate, email)

            // then
            assertAll(
                { assertThat(saved.loginId).isEqualTo(loginId) },
                { verify(exactly = 1) { userRepository.save(any()) } },
            )
        }
    }
}
