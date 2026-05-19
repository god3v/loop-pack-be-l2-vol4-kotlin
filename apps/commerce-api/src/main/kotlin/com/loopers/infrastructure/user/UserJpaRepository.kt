package com.loopers.infrastructure.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserEntity, Long> {
    fun findByLoginId(loginId: String): UserEntity?
    fun findByEmail(email: String): UserEntity?
}
