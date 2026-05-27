package com.loopers.domain.user

import com.loopers.support.error.CoreException

class Email internal constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Email) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]+$")

        fun of(value: String): Email {
            if (value.length > 255 || !value.matches(EMAIL_REGEX)) {
                throw CoreException(UserErrorType.SIGNUP_BAD_REQUEST, "email 형식이 올바르지 않습니다.")
            }
            return Email(value)
        }
    }
}
