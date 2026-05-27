package com.loopers.infrastructure.user

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.Email
import com.loopers.domain.user.Password
import com.loopers.domain.user.User
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "users")
class UserEntity private constructor(
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

    fun toDomain(): User = User(
        id = this.id,
        loginId = this.loginId,
        password = Password(this.password),
        name = this.name,
        birthDate = this.birthDate,
        email = Email(this.email),
    )

    fun syncFrom(user: User) {
        this.password = user.password.value
    }

    companion object {
        fun from(user: User): UserEntity = UserEntity(
            loginId = user.loginId,
            password = user.password.value,
            name = user.name,
            birthDate = user.birthDate,
            email = user.email.value,
        )
    }
}
