package com.loopers.domain.user

import java.security.MessageDigest

/*
TODO 임시 구현 — salt 없는 SHA-256 은 비밀번호 저장에 부적합하다 (rainbow table / brute force 취약).
Spring Security 도입 시 BCryptPasswordEncoder 등으로 교체할 것.
 */
object PasswordEncryptionUtil {
    fun encode(rawPlain: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(rawPlain.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
