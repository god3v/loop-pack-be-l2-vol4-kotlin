package com.loopers.domain.user

import com.loopers.support.error.CoreException
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
        if (userRepository.findByLoginId(loginId) != null) {
            throw CoreException(UserErrorType.DUPLICATE_LOGIN_ID)
        }
        if (userRepository.findByEmail(email) != null) {
            throw CoreException(UserErrorType.DUPLICATE_EMAIL)
        }
        val user = User.signUp(loginId, password, name, birthDate, email)
        return userRepository.save(user)
    }
}
