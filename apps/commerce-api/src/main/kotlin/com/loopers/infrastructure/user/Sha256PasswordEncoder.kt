package com.loopers.infrastructure.user

import com.loopers.domain.user.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.MessageDigest

/*
TODO 임시 구현 — salt 없는 SHA-256 은 비밀번호 저장에 부적합하다 (rainbow table / brute force 취약).
Spring Security 도입 시 BCryptPasswordEncoder 등으로 교체할 것.
 */
@Component
class Sha256PasswordEncoder : PasswordEncoder {
    override fun encode(rawPlain: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(rawPlain.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun matches(rawPlain: String, encoded: String): Boolean = encode(rawPlain) == encoded
}
