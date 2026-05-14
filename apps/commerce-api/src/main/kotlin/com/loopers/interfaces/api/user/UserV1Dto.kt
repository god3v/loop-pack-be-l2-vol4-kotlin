package com.loopers.interfaces.api.user

import com.loopers.application.user.command.ChangePasswordCommand
import com.loopers.application.user.command.SignupCommand
import com.loopers.application.user.result.MyInfoResult
import com.loopers.application.user.result.SignupResult
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

    data class SignupResponse(
        val id: Long,
        val loginId: String,
    ) {
        companion object {
            fun from(result: SignupResult): SignupResponse {
                return SignupResponse(
                    id = result.id,
                    loginId = result.loginId,
                )
            }
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
                    name = result.maskedName,
                    birthDate = result.birthDate,
                    email = result.email,
                )
            }
        }
    }

    data class ChangePasswordRequest(
        val currentPassword: String,
        val newPassword: String,
    ) {
        fun toCommand(loginId: String, headerPassword: String): ChangePasswordCommand {
            return ChangePasswordCommand(
                loginId = loginId,
                headerPassword = headerPassword,
                currentPassword = currentPassword,
                newPassword = newPassword,
            )
        }
    }
}
