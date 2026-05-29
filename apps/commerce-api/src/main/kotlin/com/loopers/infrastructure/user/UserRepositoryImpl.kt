package com.loopers.infrastructure.user

import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.User
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: User): User =
        userJpaRepository.save(UserEntity.from(user)).toDomain()

    override fun update(user: User): User {
        val entity = userJpaRepository.findById(user.id)
            .orElseThrow { IllegalStateException("user with id=${user.id} not found while updating") }
            .apply { syncFrom(user) }
        return userJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): User? =
        userJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByLoginId(loginId: String): User? =
        userJpaRepository.findByLoginId(loginId)?.toDomain()

    override fun findByEmail(email: String): User? =
        userJpaRepository.findByEmail(email)?.toDomain()
}
