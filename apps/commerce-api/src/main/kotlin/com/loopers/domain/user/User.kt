package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

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

    companion object {
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
