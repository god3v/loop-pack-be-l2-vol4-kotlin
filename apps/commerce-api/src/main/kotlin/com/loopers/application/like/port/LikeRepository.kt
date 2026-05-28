package com.loopers.application.like.port

import com.loopers.domain.like.Like

interface LikeRepository {
    fun save(like: Like): Like
    fun findByUserIdAndProductId(userId: Long, productId: Long): Like?
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean
    fun delete(like: Like)
    fun findAllByUserId(userId: Long, page: Int, size: Int): List<Like>
}
