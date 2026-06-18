package com.loopers.application.product.port

import com.loopers.application.product.result.ProductSummaryResult
import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSortType
import com.loopers.support.page.PageResult

/**
 * 상품 조회 캐시 (outbound port).
 *
 * 어댑터 구현은 캐시 저장소 장애를 흡수해야 한다 — get 은 null(=miss), put/evict 은 no-op.
 * 그래야 Facade 의 read-through 가 캐시 미스/저장소 다운에도 DB 로 정상 폴백한다.
 *
 * 이 포트 추상화 덕분에 추후 로컬 캐시(L1)를 "L1 → L2(Redis) → DB" 복합 어댑터로 끼워도
 * Facade 는 변경되지 않는다.
 */
interface ProductCache {
    fun getDetail(productId: Long): CachedProductDetail?
    fun putDetail(detail: CachedProductDetail)
    fun evictDetail(productId: Long)

    fun getList(brandId: Long?, sort: ProductSortType, page: Int, size: Int): PageResult<ProductSummaryResult>?
    fun putList(
        brandId: Long?,
        sort: ProductSortType,
        page: Int,
        size: Int,
        result: PageResult<ProductSummaryResult>,
    )
}

/**
 * 상세 조회의 캐시 가능한 "공유" 부분. 유저별 값인 likedByMe 는 캐시하지 않고 매 요청 합성한다.
 */
data class CachedProductDetail(
    val id: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val brandId: Long,
    val brandName: String,
) {
    companion object {
        fun of(product: Product, brand: Brand): CachedProductDetail = CachedProductDetail(
            id = product.id,
            name = product.name.value,
            price = product.price.value,
            likeCount = product.likeCount,
            brandId = brand.id,
            brandName = brand.name.value,
        )
    }
}
