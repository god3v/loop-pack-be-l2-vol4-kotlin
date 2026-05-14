package com.loopers.application.user.command

data class ChangePasswordCommand(
    val loginId: String,
    val headerPassword: String,
    val currentPassword: String,
    val newPassword: String,
)
