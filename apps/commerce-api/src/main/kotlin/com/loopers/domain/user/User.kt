package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
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
    }

    companion object {
        private val LOGIN_ID_REGEX = Regex("^[A-Za-z0-9]+$")
        private val NAME_REGEX = Regex("^[가-힣A-Za-z]+$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]+$")

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
