package com.loopers.application.user

import com.loopers.domain.user.User

data class UserInfo(
    val id: Long,
    val loginId: String,
) {
    companion object {
        fun from(user: User): UserInfo {
            return UserInfo(
                id = user.id,
                loginId = user.loginId,
            )
        }
    }
}
