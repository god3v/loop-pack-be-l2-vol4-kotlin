package com.loopers.application.user.result

import com.loopers.domain.user.User
import java.time.LocalDate

data class MyInfoResult(
    val loginId: String,
    val maskedName: String,
    val birthDate: LocalDate,
    val email: String,
) {
    companion object {
        fun from(user: User): MyInfoResult {
            return MyInfoResult(
                loginId = user.loginId,
                maskedName = user.maskedName(),
                birthDate = user.birthDate,
                email = user.email.value,
            )
        }
    }
}
