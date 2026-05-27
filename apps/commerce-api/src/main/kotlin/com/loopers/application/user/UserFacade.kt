package com.loopers.application.user

import com.loopers.application.user.command.ChangePasswordCommand
import com.loopers.application.user.command.SignupCommand
import com.loopers.application.user.result.MyInfoResult
import com.loopers.application.user.result.SignupResult
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserFacade(
    private val userService: UserService,
) {
    @Transactional
    fun signup(command: SignupCommand): SignupResult {
        return userService.signup(
            loginId = command.loginId,
            password = command.password,
            name = command.name,
            birthDate = command.birthDate,
            email = command.email,
        ).let { SignupResult.from(it) }
    }

    @Transactional(readOnly = true)
    fun getMyInfo(loginId: String): MyInfoResult {
        return MyInfoResult.from(userService.getByLoginId(loginId))
    }

    @Transactional
    fun changePassword(command: ChangePasswordCommand) {
        userService.changePassword(
            loginId = command.loginId,
            prevPw = command.prevPw,
            nextPw = command.nextPw,
        )
    }
}
