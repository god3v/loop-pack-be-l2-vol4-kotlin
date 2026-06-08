package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeRepository
import com.loopers.domain.like.Like
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
) : LikeRepository {
    override fun save(like: Like): Like =
        likeJpaRepository.save(LikeEntity.from(like)).toDomain()

    override fun findByUserIdAndProductId(userId: Long, productId: Long): Like? =
        likeJpaRepository.findByUserIdAndProductId(userId, productId)?.toDomain()

    override fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean =
        likeJpaRepository.existsByUserIdAndProductId(userId, productId)

    override fun delete(like: Like) {
        likeJpaRepository.deleteById(like.id)
    }

    override fun findAllByUserId(userId: Long, page: Int, size: Int): List<Like> =
        likeJpaRepository.findAllByUserId(
            userId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        ).map { it.toDomain() }
}
