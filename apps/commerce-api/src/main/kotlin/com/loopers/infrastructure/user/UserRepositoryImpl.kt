package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: User): User {
        val entity = if (user.id == 0L) {
            UserEntity.from(user)
        } else {
            userJpaRepository.findById(user.id).orElseThrow().apply { syncFrom(user) }
        }
        return userJpaRepository.save(entity).toDomain()
    }

    override fun findByLoginId(loginId: String): User? =
        userJpaRepository.findByLoginId(loginId)?.toDomain()

    override fun findByEmail(email: String): User? =
        userJpaRepository.findByEmail(email)?.toDomain()
}
