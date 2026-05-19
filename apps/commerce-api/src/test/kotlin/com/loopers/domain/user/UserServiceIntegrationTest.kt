package com.loopers.domain.user

import com.loopers.domain.user.UserFixture.DEFAULT_BIRTH_DATE
import com.loopers.domain.user.UserFixture.DEFAULT_EMAIL
import com.loopers.domain.user.UserFixture.DEFAULT_LOGIN_ID
import com.loopers.domain.user.UserFixture.DEFAULT_NAME
import com.loopers.domain.user.UserFixture.DEFAULT_PASSWORD
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
        @DisplayName("유효한 입력이면, User 가 영속되고 id 가 발급된다.")
        @Test
        fun savesUserWithGeneratedId_whenValidInput() {
            // when
            val saved = userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)

            // then
            val reloaded = userJpaRepository.findById(saved.id).orElseThrow()
            assertAll(
                { assertThat(saved.id).isPositive() },
                { assertThat(reloaded.loginId).isEqualTo(DEFAULT_LOGIN_ID) },
                { assertThat(reloaded.email).isEqualTo(DEFAULT_EMAIL) },
                { assertThat(reloaded.createdAt).isNotNull() },
                { assertThat(reloaded.updatedAt).isNotNull() },
            )
        }

        @DisplayName("동일한 loginId 가 이미 존재하면, DUPLICATE_LOGIN_ID 예외가 발생한다.")
        @Test
        fun throwsDuplicateLoginIdException_whenLoginIdAlreadyExists() {
            // give
            userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)

            // when
            val result = assertThrows<CoreException> {
                userService.signup(DEFAULT_LOGIN_ID, "Bsdf1234!", "박철수", LocalDate.of(1990, 1, 1), "other@example.com")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID)
        }

        @DisplayName("동일한 email 이 이미 존재하면, DUPLICATE_EMAIL 예외가 발생한다.")
        @Test
        fun throwsDuplicateEmailException_whenEmailAlreadyExists() {
            // give
            userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)

            // when
            val result = assertThrows<CoreException> {
                userService.signup("anothername", "Bsdf1234!", "박철수", LocalDate.of(1990, 1, 1), DEFAULT_EMAIL)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_EMAIL)
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    inner class ChangePassword {
        private val newPassword = "NewPw5678!"

        @DisplayName("정상 입력으로 비밀번호를 변경하면, DB 의 password 가 새 값으로 갱신되고 새 비번으로 다시 인증이 가능하다.")
        @Test
        fun updatesPasswordAndAuthenticatesWithNew_whenValidInput() {
            // give
            userService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTH_DATE, DEFAULT_EMAIL)

            // when
            userService.changePassword(
                loginId = DEFAULT_LOGIN_ID,
                prevPw = DEFAULT_PASSWORD,
                nextPw = newPassword,
            )

            // then
            val reloaded = userJpaRepository.findByLoginId(DEFAULT_LOGIN_ID)!!
            val reAuthenticated = userService.authenticate(DEFAULT_LOGIN_ID, newPassword)
            assertAll(
                { assertThat(reloaded.password).isNotEqualTo(newPassword) },
                { assertThat(UserFixture.DEFAULT_PASSWORD_ENCODER.matches(newPassword, reloaded.password)).isTrue() },
                { assertThat(reAuthenticated.loginId).isEqualTo(DEFAULT_LOGIN_ID) },
                {
                    assertThrows<CoreException> {
                        userService.authenticate(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD)
                    }.also { assertThat(it.errorType).isEqualTo(UserErrorType.UNAUTHORIZED) }
                },
            )
        }
    }
}
