package com.loopers.application.user.result

import com.loopers.domain.user.User
import java.time.LocalDate

data class MyInfoResult(
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
) {
    companion object {
        fun from(user: User): MyInfoResult {
            return MyInfoResult(
                loginId = user.loginId,
                name = user.name().dropLast(1) + "*",
                birthDate = user.birthDate,
                email = user.email.value,
            )
        }
    }
}
