package com.loopers.domain.user

import com.loopers.support.error.CoreException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class User internal constructor(
    val id: Long,
    val loginId: String,
    password: Password,
    val name: String,
    val birthDate: LocalDate,
    val email: Email,
) {
    var password: Password = password
        private set

    fun maskedName(): String = name.dropLast(1) + "*"

    fun changePassword(newPasswordPlain: String) {
        if (this.password.matches(newPasswordPlain)) {
            throw CoreException(UserErrorType.PASSWORD_CHANGE_BAD_REQUEST, "현재 비밀번호와 동일합니다.")
        }
        this.password = Password.of(newPasswordPlain, birthDate, UserErrorType.PASSWORD_CHANGE_BAD_REQUEST)
    }

    companion object {
        private val LOGIN_ID_REGEX = Regex("^[A-Za-z0-9]+$")
        private val NAME_REGEX = Regex("^[가-힣A-Za-z]+$")

        fun signUp(
            loginId: String,
            password: String,
            name: String,
            birthDate: LocalDate,
            email: String,
        ): User {
            if (loginId.length !in 4..20 || !loginId.matches(LOGIN_ID_REGEX)) {
                throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "loginId 형식이 올바르지 않습니다.")
            }
            if (name.length !in 2..50 || !name.matches(NAME_REGEX)) {
                throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "name 형식이 올바르지 않습니다.")
            }
            val today = LocalDate.now()
            if (birthDate.isAfter(today) || ChronoUnit.YEARS.between(birthDate, today) < 14) {
                throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "birthDate 형식이 올바르지 않습니다.")
            }
            return User(
                id = 0,
                loginId = loginId,
                password = Password.of(password, birthDate, UserErrorType.SIGNUP_BAD_REQUEST),
                name = name,
                birthDate = birthDate,
                email = Email.of(email),
            )
        }
    }
}
