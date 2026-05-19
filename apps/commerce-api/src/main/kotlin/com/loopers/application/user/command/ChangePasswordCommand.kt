package com.loopers.application.user.command

data class ChangePasswordCommand(
    val loginId: String,
    val loginPw: String,
    val prevPw: String,
    val nextPw: String,
)
