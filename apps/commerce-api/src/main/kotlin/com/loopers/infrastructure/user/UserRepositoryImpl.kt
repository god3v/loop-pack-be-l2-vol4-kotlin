package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: User): User = userJpaRepository.save(user)

    override fun findByLoginId(loginId: String): User? = userJpaRepository.findByLoginId(loginId)

    override fun findByEmail(email: String): User? = userJpaRepository.findByEmail(email)
}
