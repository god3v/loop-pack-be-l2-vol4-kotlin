package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeRepository
import com.loopers.domain.like.Like
import com.loopers.support.page.PageResult
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

    // 업무 키(userId, productId) 기반 삭제로 멱등 보장 — 동시 삭제 race 로 행이 이미 사라져도
    // 영향 행 0 으로 no-op 이 되어 deleteById 의 EmptyResultDataAccessException 전파를 막는다.
    override fun delete(like: Like) {
        likeJpaRepository.deleteByUserIdAndProductId(like.userId, like.productId)
    }

    override fun findAllByUserId(userId: Long, page: Int, size: Int): PageResult<Like> {
        val found = likeJpaRepository.findAllByUserId(
            userId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        return PageResult(
            content = found.content.map { it.toDomain() },
            page = found.number,
            size = found.size,
            totalElements = found.totalElements,
            totalPages = found.totalPages,
        )
    }
}
