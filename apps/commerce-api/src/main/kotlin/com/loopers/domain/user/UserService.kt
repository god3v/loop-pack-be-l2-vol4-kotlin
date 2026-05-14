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

    /*
    TODO 추후 인터셉터에서 인증 책임 분리
     */
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
        loginPw: String,
        prevPw: String,
        nextPw: String,
    ): User {
        val user = authenticate(loginId, loginPw)
        user.changePassword(prevPw, nextPw)
        return userRepository.save(user)
    }
}
