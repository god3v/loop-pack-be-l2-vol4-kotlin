package com.loopers.domain.user

import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원가입을 할 때, ")
    @Nested
    inner class SignUp {
        @DisplayName("정상 입력이면, User 가 영속되고 id 가 발급된다.")
        @Test
        fun savesUserWithGeneratedId_whenValidInput() {
            // when
            val saved = userService.signup(
                loginId = "goryeojin",
                password = "Asdf1234!",
                name = "고려진",
                birthDate = LocalDate.of(2001, 7, 9),
                email = "goryeojin@example.com",
            )

            // then
            val reloaded = userJpaRepository.findById(saved.id).orElseThrow()
            assertAll(
                { assertThat(saved.id).isPositive() },
                { assertThat(reloaded.loginId).isEqualTo("goryeojin") },
                { assertThat(reloaded.email).isEqualTo("goryeojin@example.com") },
                { assertThat(reloaded.createdAt).isNotNull() },
                { assertThat(reloaded.updatedAt).isNotNull() },
            )
        }

        @DisplayName("동일한 loginId 가 이미 존재하면, DUPLICATE_LOGIN_ID 예외가 발생한다.")
        @Test
        fun throwsDuplicateLoginIdException_whenLoginIdAlreadyExists() {
            // arrange
            userService.signup(
                loginId = "goryeojin",
                password = "Asdf1234!",
                name = "고려진",
                birthDate = LocalDate.of(2001, 7, 9),
                email = "goryeojin@example.com",
            )

            // when
            val result = assertThrows<CoreException> {
                userService.signup(
                    loginId = "goryeojin",
                    password = "Bsdf1234!",
                    name = "박철수",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "other@example.com",
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID)
        }

        @DisplayName("동일한 email 이 이미 존재하면, DUPLICATE_EMAIL 예외가 발생한다.")
        @Test
        fun throwsDuplicateEmailException_whenEmailAlreadyExists() {
            // arrange
            userService.signup(
                loginId = "goryeojin",
                password = "Asdf1234!",
                name = "고려진",
                birthDate = LocalDate.of(2001, 7, 9),
                email = "goryeojin@example.com",
            )

            // when
            val result = assertThrows<CoreException> {
                userService.signup(
                    loginId = "anothername",
                    password = "Bsdf1234!",
                    name = "박철수",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "goryeojin@example.com",
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_EMAIL)
        }
    }
}
