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

    fun authenticate(loginId: String, plainPassword: String): User {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        if (!user.password.matches(plainPassword)) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        return user
    }

    fun changePassword(
        loginId: String,
        headerPassword: String,
        currentPassword: String,
        newPassword: String,
    ): User {
        val user = authenticate(loginId, headerPassword)
        if (!user.password.matches(currentPassword)) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        user.changePassword(newPassword)
        return userRepository.save(user)
    }
}
