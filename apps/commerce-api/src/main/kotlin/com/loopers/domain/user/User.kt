package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Entity
@Table(name = "users")
class User private constructor(
    loginId: String,
    password: String,
    name: String,
    birthDate: LocalDate,
    email: String,
) : BaseEntity() {
    var loginId: String = loginId
        protected set

    var password: String = password
        protected set

    var name: String = name
        protected set

    var birthDate: LocalDate = birthDate
        protected set

    var email: String = email
        protected set

    init {
        if (loginId.length !in 4..20 || !loginId.matches(LOGIN_ID_REGEX)) {
            throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "loginId 형식이 올바르지 않습니다.")
        }
        if (name.length !in 2..50 || !name.matches(NAME_REGEX)) {
            throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "name 형식이 올바르지 않습니다.")
        }
        if (email.length > 255 || !email.matches(EMAIL_REGEX)) {
            throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "email 형식이 올바르지 않습니다.")
        }
        val today = LocalDate.now()
        if (birthDate.isAfter(today) || ChronoUnit.YEARS.between(birthDate, today) < 14) {
            throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "birthDate 형식이 올바르지 않습니다.")
        }
        if (password.length !in 8..16) {
            throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "비밀번호 길이는 8~16자여야 합니다.")
        }
        if (password.any { it !in '!'..'~' }) {
            throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "비밀번호에 허용되지 않은 문자가 포함되어 있습니다.")
        }
        val hasAlpha = password.any { it in 'A'..'Z' || it in 'a'..'z' }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        if (!(hasAlpha && hasDigit && hasSpecial)) {
            throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "비밀번호는 영문/숫자/특수문자를 모두 포함해야 합니다.")
        }
        val birthDateYyyyMmDd = birthDate.format(YYYY_MM_DD_FORMATTER)
        val birthDateYyMmDd = birthDate.format(YY_MM_DD_FORMATTER)
        if (password.contains(birthDateYyyyMmDd) || password.contains(birthDateYyMmDd)) {
            throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
        }
    }

    fun maskedName(): String = name.dropLast(1) + "*"

    companion object {
        private val LOGIN_ID_REGEX = Regex("^[A-Za-z0-9]+$")
        private val NAME_REGEX = Regex("^[가-힣A-Za-z]+$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]+$")
        private val YYYY_MM_DD_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val YY_MM_DD_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")

        fun signUp(
            loginId: String,
            password: String,
            name: String,
            birthDate: LocalDate,
            email: String,
        ): User = User(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = birthDate,
            email = email,
        )
    }
}
