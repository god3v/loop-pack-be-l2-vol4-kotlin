package com.loopers.application.product

import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.like.LikeRepository
import com.loopers.application.product.command.RegisterProductCommand
import com.loopers.application.product.command.UpdateProductCommand
import com.loopers.application.product.port.CachedProductDetail
import com.loopers.application.product.port.ProductCache
import com.loopers.application.product.query.GetProductsQuery
import com.loopers.domain.product.ProductRepository
import com.loopers.application.product.result.AdminProductDetailResult
import com.loopers.application.product.result.AdminProductSummaryResult
import com.loopers.application.product.result.ProductDetailResult
import com.loopers.application.product.result.ProductSummaryResult
import com.loopers.domain.brand.BrandErrorType
import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductEvent
import com.loopers.domain.product.ProductSortType
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageQuery
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val likeRepository: LikeRepository,
    private val productCache: ProductCache,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional(readOnly = true)
    fun getProducts(query: GetProductsQuery): PageResult<ProductSummaryResult> {
        val sort = query.sort ?: ProductSortType.LATEST
        val page = query.paging.page
        val size = query.paging.size
        val cacheable = page < LIST_CACHE_MAX_PAGE
        if (cacheable) {
            productCache.getList(query.brandId, sort, page, size)?.let { return it }
        }
        val result = productRepository.findAll(sort, query.brandId, page, size)
            .map { ProductSummaryResult.from(it) }
        if (cacheable) {
            productCache.putList(query.brandId, sort, page, size, result)
        }
        return result
    }

    @Transactional(readOnly = true)
    fun getProductDetail(productId: Long, userId: Long?): ProductDetailResult {
        // 공유 본문(product+brand)은 캐시 read-through, 유저별 likedByMe 는 매 요청 합성한다.
        val detail = productCache.getDetail(productId) ?: run {
            val product = productRepository.findById(productId)
                ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
            val brand = brandRepository.findById(product.brandId)
                ?: throw CoreException(BrandErrorType.BRAND_NOT_FOUND)
            CachedProductDetail.of(product, brand).also { productCache.putDetail(it) }
        }
        val likedByMe = userId != null && likeRepository.existsByUserIdAndProductId(userId, productId)

        eventPublisher.publish(ProductEvent.Viewed(productId = productId, userId = userId))
        return ProductDetailResult(
            id = detail.id,
            name = detail.name,
            price = detail.price,
            likeCount = detail.likeCount,
            brandId = detail.brandId,
            brandName = detail.brandName,
            likedByMe = likedByMe,
        )
    }

    @Transactional(readOnly = true)
    fun getProductsForAdmin(brandId: Long?, pageQuery: PageQuery): PageResult<AdminProductSummaryResult> =
        productRepository
            .findAllForAdmin(brandId, pageQuery.page, pageQuery.size)
            .map { AdminProductSummaryResult.from(it) }

    @Transactional(readOnly = true)
    fun getProductForAdmin(productId: Long): AdminProductDetailResult {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
        val brand = brandRepository.findById(product.brandId)
            ?: throw CoreException(BrandErrorType.BRAND_NOT_FOUND)
        return AdminProductDetailResult.of(product, brand)
    }

    @Transactional
    fun registerProduct(command: RegisterProductCommand): AdminProductDetailResult {
        val brand = brandRepository.findById(command.brandId)
            ?: throw CoreException(BrandErrorType.BRAND_NOT_FOUND)
        if (productRepository.existsByBrandIdAndName(command.brandId, command.name)) {
            throw CoreException(ProductErrorType.DUPLICATE_PRODUCT_NAME)
        }
        val product = Product.create(
            name = command.name,
            price = command.price,
            stock = command.stock,
            likeCount = 0L,
            brandId = command.brandId,
        )
        return AdminProductDetailResult.of(productRepository.save(product), brand)
    }

    @Transactional
    fun updateProduct(productId: Long, command: UpdateProductCommand): AdminProductDetailResult {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
        if (product.name.value != command.name &&
            productRepository.existsByBrandIdAndName(product.brandId, command.name)
        ) {
            throw CoreException(ProductErrorType.DUPLICATE_PRODUCT_NAME)
        }
        product.update(command.name, command.price, command.salesStatus)
        val brand = brandRepository.findById(product.brandId)
            ?: throw CoreException(BrandErrorType.BRAND_NOT_FOUND)
        val result = AdminProductDetailResult.of(productRepository.save(product), brand)
        productCache.evictDetail(productId)
        return result
    }

    @Transactional
    fun deleteProduct(productId: Long) {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
        product.softDelete()
        productRepository.save(product)
        productCache.evictDetail(productId)
    }

    companion object {
        // 캐시 대상 목록 페이지 상한 (0-based). page < N 만 캐시한다.
        private const val LIST_CACHE_MAX_PAGE = 5
    }
}
