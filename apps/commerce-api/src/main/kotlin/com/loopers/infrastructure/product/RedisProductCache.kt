package com.loopers.infrastructure.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.port.CachedProductDetail
import com.loopers.application.product.port.ProductCache
import com.loopers.application.product.result.ProductSummaryResult
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.product.ProductSortType
import com.loopers.support.page.PageResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 상품 조회 Redis 캐시 어댑터.
 *
 * - 직렬화: Jackson JSON (String value). 키에 버전(v1) 을 넣어 스키마 변경 시 롤오버 가능.
 * - 장애 흡수: 모든 Redis 호출을 runCatching 으로 감싸 get 은 null(miss), put/evict 은 no-op 로 폴백한다.
 *   → Redis 가 죽어도 Facade 는 캐시 미스로 보고 DB 로 정상 동작한다.
 * - 무효화: 상세는 TTL + 상품 수정/삭제 시 evict, 목록은 정밀 무효화가 불가능해 TTL-only.
 */
@Component
class RedisProductCache(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : ProductCache {
    private val log = LoggerFactory.getLogger(javaClass)
    private val listType = objectMapper.typeFactory
        .constructParametricType(PageResult::class.java, ProductSummaryResult::class.java)

    override fun getDetail(productId: Long): CachedProductDetail? = runCatching {
        redisTemplate.opsForValue().get(detailKey(productId))
            ?.let { objectMapper.readValue(it, CachedProductDetail::class.java) }
    }.getOrElse {
        log.warn("product detail cache get 실패 (productId={})", productId, it)
        null
    }

    override fun putDetail(detail: CachedProductDetail) = write(detailKey(detail.id), detail, DETAIL_TTL)

    override fun evictDetail(productId: Long) {
        runCatching { redisTemplate.delete(detailKey(productId)) }
            .onFailure { log.warn("product detail cache evict 실패 (productId={})", productId, it) }
    }

    override fun getList(
        brandId: Long?,
        sort: ProductSortType,
        page: Int,
        size: Int,
    ): PageResult<ProductSummaryResult>? = runCatching {
        val json = redisTemplate.opsForValue().get(listKey(brandId, sort, page, size)) ?: return@runCatching null
        @Suppress("UNCHECKED_CAST")
        objectMapper.readValue(json, listType) as PageResult<ProductSummaryResult>
    }.getOrElse {
        log.warn("product list cache get 실패 (brandId={}, sort={}, page={})", brandId, sort, page, it)
        null
    }

    override fun putList(
        brandId: Long?,
        sort: ProductSortType,
        page: Int,
        size: Int,
        result: PageResult<ProductSummaryResult>,
    ) = write(listKey(brandId, sort, page, size), result, LIST_TTL)

    private fun write(key: String, value: Any, ttl: Duration) {
        runCatching {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl)
        }.onFailure { log.warn("cache put 실패 (key={})", key, it) }
    }

    private fun detailKey(productId: Long): String = "product:detail:v1:$productId"

    private fun listKey(brandId: Long?, sort: ProductSortType, page: Int, size: Int): String =
        "product:list:v1:${brandId ?: "all"}:${sort.key}:$page:$size"

    companion object {
        private val DETAIL_TTL: Duration = Duration.ofMinutes(5)
        private val LIST_TTL: Duration = Duration.ofSeconds(60)
    }
}
