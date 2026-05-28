package com.loopers.application.like

import com.loopers.application.like.port.LikeRepository
import com.loopers.application.product.port.ProductRepository
import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeErrorType
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductFixture
import com.loopers.support.error.CoreException
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("LikeFacade")
class LikeFacadeTest {
    private val likeRepository: LikeRepository = mockk()
    private val productRepository: ProductRepository = mockk()
    private val likeFacade = LikeFacade(likeRepository, productRepository)

    @Nested
    @DisplayName("like — UC-1 멱등 등록")
    inner class LikeRegister {
        @Test
        @DisplayName("신규 호출 시 Like 가 저장되고 Product.likeCount 가 1 증가한다")
        fun newLikeIncreasesLikeCount() {
            val product = ProductFixture.validProduct(likeCount = 3L)
            every { productRepository.findById(1L) } returns product
            every { likeRepository.existsByUserIdAndProductId(7L, 1L) } returns false
            every { likeRepository.save(any()) } answers { firstArg() }
            every { productRepository.save(product) } returns product

            likeFacade.like(userId = 7L, productId = 1L)

            assertThat(product.likeCount).isEqualTo(4L)
            verify { likeRepository.save(any()) }
            verify { productRepository.save(product) }
        }

        @Test
        @DisplayName("이미 좋아요한 상태에서 재호출하면 추가 저장 없이 멱등 통과한다")
        fun idempotentWhenAlreadyLiked() {
            val product = ProductFixture.validProduct(likeCount = 3L)
            every { productRepository.findById(1L) } returns product
            every { likeRepository.existsByUserIdAndProductId(7L, 1L) } returns true

            likeFacade.like(userId = 7L, productId = 1L)

            assertThat(product.likeCount).isEqualTo(3L)
            verify(exactly = 0) { likeRepository.save(any()) }
            verify(exactly = 0) { productRepository.save(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenProductMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { likeFacade.like(userId = 7L, productId = 99L) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
            verify(exactly = 0) { likeRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("unlike — UC-2 멱등 취소")
    inner class Unlike {
        @Test
        @DisplayName("좋아요 행이 있으면 제거되고 Product.likeCount 가 1 감소한다")
        fun removesLikeAndDecreasesCount() {
            val product = ProductFixture.validProduct(likeCount = 5L)
            val existing = Like.create(userId = 7L, productId = 1L)
            every { productRepository.findById(1L) } returns product
            every { likeRepository.findByUserIdAndProductId(7L, 1L) } returns existing
            every { likeRepository.delete(existing) } just Runs
            every { productRepository.save(product) } returns product

            likeFacade.unlike(userId = 7L, productId = 1L)

            assertThat(product.likeCount).isEqualTo(4L)
            verify { likeRepository.delete(existing) }
            verify { productRepository.save(product) }
        }

        @Test
        @DisplayName("좋아요가 없는 상태에서 호출하면 무동작 멱등 통과한다")
        fun idempotentWhenNotLiked() {
            val product = ProductFixture.validProduct(likeCount = 5L)
            every { productRepository.findById(1L) } returns product
            every { likeRepository.findByUserIdAndProductId(7L, 1L) } returns null

            likeFacade.unlike(userId = 7L, productId = 1L)

            assertThat(product.likeCount).isEqualTo(5L)
            verify(exactly = 0) { likeRepository.delete(any()) }
            verify(exactly = 0) { productRepository.save(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenProductMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { likeFacade.unlike(userId = 7L, productId = 99L) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
            verify(exactly = 0) { likeRepository.delete(any()) }
        }
    }

    @Nested
    @DisplayName("getMyLikes — UC-3 내 목록")
    inner class GetMyLikes {
        @Test
        @DisplayName("본인 식별자로 호출하면 좋아요한 상품 요약 목록이 반환된다")
        fun returnsLikedProducts() {
            val product1 = ProductFixture.validProduct(name = "A")
            val product2 = ProductFixture.validProduct(name = "B")
            val like1 = Like.create(userId = 7L, productId = 1L)
            val like2 = Like.create(userId = 7L, productId = 2L)
            every { likeRepository.findAllByUserId(7L, 0, 20) } returns listOf(like1, like2)
            every { productRepository.findById(1L) } returns product1
            every { productRepository.findById(2L) } returns product2

            val result = likeFacade.getMyLikes(authedUserId = 7L, requestedUserId = 7L, page = 0, size = 20)

            assertThat(result.map { it.name }).containsExactly("A", "B")
        }

        @Test
        @DisplayName("좋아요한 상품이 없으면 빈 목록이 반환된다")
        fun returnsEmptyWhenNoLikes() {
            every { likeRepository.findAllByUserId(7L, 0, 20) } returns emptyList()

            val result = likeFacade.getMyLikes(authedUserId = 7L, requestedUserId = 7L, page = 0, size = 20)

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("인증된 회원과 다른 식별자로 호출하면 LIKE_FORBIDDEN 예외가 발생한다")
        fun throwsWhenOtherUser() {
            val ex = assertThrows<CoreException> {
                likeFacade.getMyLikes(authedUserId = 7L, requestedUserId = 8L, page = 0, size = 20)
            }
            assertThat(ex.errorType).isEqualTo(LikeErrorType.LIKE_FORBIDDEN)
            verify(exactly = 0) { likeRepository.findAllByUserId(any(), any(), any()) }
        }

        @Test
        @DisplayName("page / size 가 Repository 에 전달된다")
        fun delegatesPaging() {
            every { likeRepository.findAllByUserId(7L, 3, 50) } returns emptyList()

            likeFacade.getMyLikes(authedUserId = 7L, requestedUserId = 7L, page = 3, size = 50)

            verify { likeRepository.findAllByUserId(7L, 3, 50) }
        }
    }
}
