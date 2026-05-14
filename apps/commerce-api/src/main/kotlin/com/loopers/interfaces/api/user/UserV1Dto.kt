package com.loopers.interfaces.api.user

import com.loopers.application.user.command.ChangePasswordCommand
import com.loopers.application.user.command.SignupCommand
import com.loopers.application.user.result.MyInfoResult
import java.time.LocalDate

class UserV1Dto {
    data class SignupRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        fun toCommand(): SignupCommand {
            return SignupCommand(
                loginId = loginId,
                password = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )
        }
    }

    data class MyInfoResponse(
        val loginId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
    ) {
        companion object {
            fun from(result: MyInfoResult): MyInfoResponse {
                return MyInfoResponse(
                    loginId = result.loginId,
                    name = result.name,
                    birthDate = result.birthDate,
                    email = result.email,
                )
            }
        }
    }

    data class ChangePasswordRequest(
        val prevPw: String,
        val nextPw: String,
    ) {
        fun toCommand(loginId: String, loginPw: String): ChangePasswordCommand {
            return ChangePasswordCommand(
                loginId = loginId,
                loginPw = loginPw,
                prevPw = prevPw,
                nextPw = nextPw,
            )
        }
    }
}
