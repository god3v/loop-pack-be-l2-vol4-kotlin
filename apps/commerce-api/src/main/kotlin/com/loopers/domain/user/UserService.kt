package com.loopers.domain.user

import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
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
        PasswordPolicy.validate(password, birthDate)
        val encoded = Password(passwordEncoder.encode(password))
        val user = User.signUp(loginId, encoded, name, birthDate, email)
        return userRepository.save(user)
    }

    fun authenticate(loginId: String, plainPassword: String): User {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        if (!passwordEncoder.matches(plainPassword, user.password.value)) {
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
        if (!passwordEncoder.matches(prevPw, user.password.value)) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        if (prevPw == nextPw) {
            throw CoreException(UserErrorType.PASSWORD_CHANGE_BAD_REQUEST, "현재 비밀번호와 동일합니다.")
        }
        PasswordPolicy.validate(nextPw, user.birthDate)
        user.changePassword(Password(passwordEncoder.encode(nextPw)))
        return userRepository.update(user)
    }
}
