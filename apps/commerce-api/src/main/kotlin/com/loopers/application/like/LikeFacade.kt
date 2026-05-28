package com.loopers.application.like

import com.loopers.application.like.port.LikeRepository
import com.loopers.application.like.result.LikedProductResult
import com.loopers.application.product.port.ProductRepository
import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeErrorType
import com.loopers.domain.product.ProductErrorType
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeRepository: LikeRepository,
    private val productRepository: ProductRepository,
) {
    @Transactional
    fun like(userId: Long, productId: Long) {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }
        likeRepository.save(Like.create(userId = userId, productId = productId))
        product.increaseLikeCount()
        productRepository.save(product)
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
        val existing = likeRepository.findByUserIdAndProductId(userId, productId) ?: return
        likeRepository.delete(existing)
        product.decreaseLikeCount()
        productRepository.save(product)
    }

    @Transactional(readOnly = true)
    fun getMyLikes(
        authedUserId: Long,
        requestedUserId: Long,
        page: Int,
        size: Int,
    ): List<LikedProductResult> {
        if (authedUserId != requestedUserId) {
            throw CoreException(LikeErrorType.LIKE_FORBIDDEN)
        }
        return likeRepository.findAllByUserId(requestedUserId, page, size)
            .mapNotNull { productRepository.findById(it.productId) }
            .map { LikedProductResult.from(it) }
    }
}
