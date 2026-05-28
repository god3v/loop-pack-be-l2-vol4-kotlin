package com.loopers.application.user.port

import com.loopers.domain.user.User

interface UserRepository {
    fun save(user: User): User
    fun update(user: User): User
    fun findById(id: Long): User?
    fun findByLoginId(loginId: String): User?
    fun findByEmail(email: String): User?
}
