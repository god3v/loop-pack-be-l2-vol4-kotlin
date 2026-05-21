package com.loopers.domain.user

import com.loopers.support.error.CoreException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object PasswordPolicy {
    private val YYYY_MM_DD_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val YY_MM_DD_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")

    fun validate(rawPlain: String, birthDate: LocalDate) {
        if (rawPlain.length !in 8..16) {
            throw CoreException(UserErrorType.INVALID_PASSWORD, "비밀번호 길이는 8~16자여야 합니다.")
        }
        if (rawPlain.any { it !in '!'..'~' }) {
            throw CoreException(UserErrorType.INVALID_PASSWORD, "비밀번호에 허용되지 않은 문자가 포함되어 있습니다.")
        }
        val hasAlpha = rawPlain.any { it in 'A'..'Z' || it in 'a'..'z' }
        val hasDigit = rawPlain.any { it.isDigit() }
        val hasSpecial = rawPlain.any { !it.isLetterOrDigit() }
        if (!(hasAlpha && hasDigit && hasSpecial)) {
            throw CoreException(UserErrorType.INVALID_PASSWORD, "비밀번호는 영문/숫자/특수문자를 모두 포함해야 합니다.")
        }
        val birthDateYyyyMmDd = birthDate.format(YYYY_MM_DD_FORMATTER)
        val birthDateYyMmDd = birthDate.format(YY_MM_DD_FORMATTER)
        if (rawPlain.contains(birthDateYyyyMmDd) || rawPlain.contains(birthDateYyMmDd)) {
            throw CoreException(UserErrorType.INVALID_PASSWORD, "비밀번호에 생년월일을 포함할 수 없습니다.")
        }
    }
}
