package com.loopers.domain.user

import com.loopers.infrastructure.user.Sha256PasswordEncoder
import java.time.LocalDate

object UserFixture {
    const val DEFAULT_LOGIN_ID = "goryeojin"
    const val DEFAULT_PASSWORD = "Asdf1234!"
    const val DEFAULT_NAME = "고려진"
    val DEFAULT_BIRTH_DATE: LocalDate = LocalDate.of(2001, 7, 9)
    const val DEFAULT_EMAIL = "goryeojin@example.com"
    val DEFAULT_PASSWORD_ENCODER: PasswordEncoder = Sha256PasswordEncoder()

    fun validUser(
        loginId: String = DEFAULT_LOGIN_ID,
        password: String = DEFAULT_PASSWORD,
        name: String = DEFAULT_NAME,
        birthDate: LocalDate = DEFAULT_BIRTH_DATE,
        email: String = DEFAULT_EMAIL,
        encoder: PasswordEncoder = DEFAULT_PASSWORD_ENCODER,
    ): User {
        PasswordPolicy.validate(password, birthDate)
        val encoded = Password(encoder.encode(password))
        return User.signUp(loginId, encoded, name, birthDate, email)
    }
}
