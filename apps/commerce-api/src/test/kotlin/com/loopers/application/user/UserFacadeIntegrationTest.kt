package com.loopers.application.user

import com.loopers.application.user.command.ChangePasswordCommand
import com.loopers.application.user.command.SignupCommand
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture
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

@SpringBootTest
class UserFacadeIntegrationTest @Autowired constructor(
    private val userFacade: UserFacade,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun validSignupCommand(
        loginId: String = DEFAULT_LOGIN_ID,
        email: String = DEFAULT_EMAIL,
    ): SignupCommand = SignupCommand(
        loginId = loginId,
        password = DEFAULT_PASSWORD,
        name = DEFAULT_NAME,
        birthDate = DEFAULT_BIRTH_DATE,
        email = email,
    )

    @DisplayName("UserFacade.signup 을 호출할 때, ")
    @Nested
    inner class Signup {
        @DisplayName("정상 입력으로 호출하면, SignupResult 가 반환되고 users 테이블에 1 row 가 영속된다.")
        @Test
        fun returnsSignupResultAndPersistsRow_whenValidInput() {
            // when
            val result = userFacade.signup(validSignupCommand())

            // then
            assertAll(
                { assertThat(result.id).isPositive() },
                { assertThat(result.loginId).isEqualTo(DEFAULT_LOGIN_ID) },
                { assertThat(userJpaRepository.count()).isEqualTo(1L) },
            )
        }

        @DisplayName("도메인 검증 실패로 예외가 던져지면, 트랜잭션이 롤백되어 DB 에 부분 저장이 없다.")
        @Test
        fun rollsBackAndPersistsNothing_whenDomainExceptionThrown() {
            // when
            val result = assertThrows<CoreException> {
                userFacade.signup(validSignupCommand(loginId = "한글포함"))
            }

            // then
            assertAll(
                { assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST) },
                { assertThat(userJpaRepository.count()).isEqualTo(0L) },
            )
        }

        @DisplayName("이미 가입된 loginId 로 호출하면, DUPLICATE_LOGIN_ID 예외가 발생하고 두 번째 row 는 영속되지 않는다.")
        @Test
        fun rollsBackOnDuplicateLoginId() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val result = assertThrows<CoreException> {
                userFacade.signup(validSignupCommand(email = "other@example.com"))
            }

            // then
            assertAll(
                { assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID) },
                { assertThat(userJpaRepository.count()).isEqualTo(1L) },
            )
        }
    }

    @DisplayName("UserFacade.getMyInfo 를 호출할 때, ")
    @Nested
    inner class GetMyInfo {
        @DisplayName("가입된 loginId 로 호출하면, 마스킹된 name 을 포함한 MyInfoResult 가 반환된다.")
        @Test
        fun returnsMaskedMyInfo_whenUserExists() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val result = userFacade.getMyInfo(DEFAULT_LOGIN_ID)

            // then
            assertAll(
                { assertThat(result.loginId).isEqualTo(DEFAULT_LOGIN_ID) },
                { assertThat(result.name).contains("*") },
                { assertThat(result.email).isEqualTo(DEFAULT_EMAIL) },
                { assertThat(result.birthDate).isEqualTo(DEFAULT_BIRTH_DATE) },
            )
        }

        @DisplayName("존재하지 않는 loginId 로 호출하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenUserNotFound() {
            // when
            val result = assertThrows<CoreException> {
                userFacade.getMyInfo("notexists")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("UserFacade.changePassword 를 호출할 때, ")
    @Nested
    inner class ChangePassword {
        private val newPassword = "NewPw5678!"

        @DisplayName("정상 입력으로 호출하면, DB 의 password 가 새 값으로 갱신된다.")
        @Test
        fun updatesPasswordInDb_whenValidInput() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            userFacade.changePassword(
                ChangePasswordCommand(
                    loginId = DEFAULT_LOGIN_ID,
                    prevPw = DEFAULT_PASSWORD,
                    nextPw = newPassword,
                ),
            )

            // then
            val reloaded = userJpaRepository.findByLoginId(DEFAULT_LOGIN_ID)!!
            assertAll(
                { assertThat(reloaded.password).isNotEqualTo(newPassword) },
                { assertThat(UserFixture.DEFAULT_PASSWORD_ENCODER.matches(newPassword, reloaded.password)).isTrue() },
            )
        }

        @DisplayName("RULE 위반 예외가 던져지면, 트랜잭션이 롤백되어 DB 의 password 가 변경되지 않는다.")
        @Test
        fun rollsBackPasswordChange_whenRuleViolated() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            assertThrows<CoreException> {
                userFacade.changePassword(
                    ChangePasswordCommand(
                        loginId = DEFAULT_LOGIN_ID,
                            prevPw = DEFAULT_PASSWORD,
                        nextPw = "short1!",
                    ),
                )
            }

            // then
            val reloaded = userJpaRepository.findByLoginId(DEFAULT_LOGIN_ID)!!
            assertAll(
                { assertThat(reloaded.password).isNotEqualTo(DEFAULT_PASSWORD) },
                { assertThat(UserFixture.DEFAULT_PASSWORD_ENCODER.matches(DEFAULT_PASSWORD, reloaded.password)).isTrue() },
            )
        }

        @DisplayName("body 의 prevPw 가 일치하지 않으면, UNAUTHORIZED 예외가 발생하고 password 가 변경되지 않는다.")
        @Test
        fun throwsUnauthorizedAndRollsBack_whenPrevPwMismatch() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val result = assertThrows<CoreException> {
                userFacade.changePassword(
                    ChangePasswordCommand(
                        loginId = DEFAULT_LOGIN_ID,
                            prevPw = "Wrong1234!",
                        nextPw = newPassword,
                    ),
                )
            }

            // then
            val reloaded = userJpaRepository.findByLoginId(DEFAULT_LOGIN_ID)!!
            assertAll(
                { assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED) },
                { assertThat(reloaded.password).isNotEqualTo(DEFAULT_PASSWORD) },
                { assertThat(UserFixture.DEFAULT_PASSWORD_ENCODER.matches(DEFAULT_PASSWORD, reloaded.password)).isTrue() },
            )
        }
    }
}
