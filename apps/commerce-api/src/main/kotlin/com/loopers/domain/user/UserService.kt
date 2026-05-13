package com.loopers.domain.user

import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class UserService(
    private val userRepository: UserRepository,
) {
    fun signup(
        loginId: String,
        password: String,
        name: String,
        birthDate: LocalDate,
        email: String,
    ): User {
        val user = User.signUp(loginId, password, name, birthDate, email)
        return userRepository.save(user)
    }
}
