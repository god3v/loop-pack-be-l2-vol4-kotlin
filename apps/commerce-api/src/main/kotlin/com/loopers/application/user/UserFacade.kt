package com.loopers.application.user

import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserFacade(
    private val userService: UserService,
) {
    @Transactional
    fun signup(command: SignupCommand): UserInfo {
        return userService.signup(
            loginId = command.loginId,
            password = command.password,
            name = command.name,
            birthDate = command.birthDate,
            email = command.email,
        ).let { UserInfo.from(it) }
    }
}
