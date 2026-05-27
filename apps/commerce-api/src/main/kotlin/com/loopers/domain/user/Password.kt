package com.loopers.domain.user

import java.time.LocalDate

class Password internal constructor(
    val value: String,
) {
    fun matches(rawPlain: String): Boolean = value == PasswordEncryptionUtil.encode(rawPlain)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Password) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "Password(****)"

    companion object {
        fun create(rawPlain: String, birthDate: LocalDate): Password {
            PasswordPolicy.validate(rawPlain, birthDate)
            return Password(PasswordEncryptionUtil.encode(rawPlain))
        }
    }
}
