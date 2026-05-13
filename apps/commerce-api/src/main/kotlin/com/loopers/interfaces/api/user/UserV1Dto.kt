package com.loopers.interfaces.api.user

import com.loopers.application.user.MyInfoResult
import com.loopers.application.user.SignupCommand
import com.loopers.application.user.UserInfo
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
            fun from(info: UserInfo): SignupResponse {
                return SignupResponse(
                    id = info.id,
                    loginId = info.loginId,
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
}
