package com.loopers.domain.user

interface PasswordEncoder {
    fun encode(rawPlain: String): String
    fun matches(rawPlain: String, encoded: String): Boolean
}
