package com.loopers.application.user.result

import com.loopers.domain.user.User

data class SignupResult(
    val id: Long,
    val loginId: String,
) {
    companion object {
        fun from(user: User): SignupResult {
            return SignupResult(
                id = user.id,
                loginId = user.loginId,
            )
        }
    }
}
