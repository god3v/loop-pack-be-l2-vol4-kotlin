package com.loopers.application.user.command

import java.time.LocalDate

data class SignupCommand(
    val loginId: String,
    val password: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
)
