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
        val user = User.signUp(loginId, Password.create(password, birthDate), name, birthDate, email)
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

    fun getByLoginId(loginId: String): User =
        userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)

    fun changePassword(
        loginId: String,
        prevPw: String,
        nextPw: String,
    ): User {
        val user = getByLoginId(loginId)
        if (!user.password.matches(prevPw)) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        if (prevPw == nextPw) {
            throw CoreException(UserErrorType.PASSWORD_CHANGE_BAD_REQUEST, "현재 비밀번호와 동일합니다.")
        }
        user.changePassword(Password.create(nextPw, user.birthDate))
        return userRepository.update(user)
    }
}
