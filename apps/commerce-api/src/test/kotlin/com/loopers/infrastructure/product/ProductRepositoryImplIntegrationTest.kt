package com.loopers.infrastructure.product

import com.loopers.application.product.port.ProductRepository
import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductSortType
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, ProductRepositoryImpl::class)
class ProductRepositoryImplIntegrationTest @Autowired constructor(
    private val productRepository: ProductRepository,
    private val testEntityManager: TestEntityManager,
) {
    private fun persist(
        name: String = "P",
        price: Int = 1000,
        likeCount: Long = 0L,
        brandId: Long = 1L,
    ): Product {
        val saved = productRepository.save(
            ProductFixture.validProduct(name = name, price = price, likeCount = likeCount, brandId = brandId),
        )
        testEntityManager.flush()
        return saved
    }

    @DisplayName("save / findById 라운드트립")
    @Nested
    inner class SaveAndFind {
        @DisplayName("save 후 findById 로 동일 도메인 객체가 복원된다.")
        @Test
        fun saveThenFindById() {
            val saved = persist(name = "M1", price = 1_000_000)
            testEntityManager.clear()

            val found = productRepository.findById(saved.id)

            assertThat(found).isNotNull()
            assertThat(found!!.name.value).isEqualTo("M1")
            assertThat(found.price.value).isEqualTo(1_000_000)
        }

        @DisplayName("findById 는 soft-deleted Product 를 null 로 반환한다.")
        @Test
        fun findByIdExcludesSoftDeleted() {
            val saved = persist()
            saved.softDelete()
            productRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            val found = productRepository.findById(saved.id)

            assertThat(found).isNull()
        }
    }

    @DisplayName("findAll(sort, brandId, page, size)")
    @Nested
    inner class FindAll {
        @DisplayName("sort=LATEST 는 createdAt desc 로 정렬한다.")
        @Test
        fun latestOrdersByCreatedAtDesc() {
            val a = persist(name = "A")
            Thread.sleep(10)
            val b = persist(name = "B")
            Thread.sleep(10)
            val c = persist(name = "C")
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.LATEST, null, 0, 10)

            assertThat(result.map { it.id }).containsExactly(c.id, b.id, a.id)
        }

        @DisplayName("sort=PRICE_ASC 는 price asc 로 정렬한다.")
        @Test
        fun priceAscOrdersByPriceAsc() {
            persist(name = "high", price = 9000)
            persist(name = "low", price = 1000)
            persist(name = "mid", price = 5000)
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.PRICE_ASC, null, 0, 10)

            assertThat(result.map { it.price.value }).containsExactly(1000, 5000, 9000)
        }

        @DisplayName("sort=LIKES_DESC 는 likeCount desc 로 정렬한다.")
        @Test
        fun likesDescOrdersByLikeCountDesc() {
            persist(name = "few", likeCount = 1)
            persist(name = "many", likeCount = 100)
            persist(name = "mid", likeCount = 30)
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.LIKES_DESC, null, 0, 10)

            assertThat(result.map { it.likeCount }).containsExactly(100L, 30L, 1L)
        }

        @DisplayName("soft-deleted Product 를 제외한다.")
        @Test
        fun excludesSoftDeleted() {
            persist(name = "live")
            val dead = persist(name = "dead")
            dead.softDelete()
            productRepository.save(dead)
            testEntityManager.flush()
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.LATEST, null, 0, 10)

            assertThat(result.map { it.name.value }).containsExactly("live")
        }

        @DisplayName("brandId 필터가 적용된다.")
        @Test
        fun appliesBrandIdFilter() {
            persist(name = "B1-1", brandId = 1L)
            persist(name = "B1-2", brandId = 1L)
            persist(name = "B2-1", brandId = 2L)
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.LATEST, 2L, 0, 10)

            assertThat(result).hasSize(1)
            assertThat(result[0].brandId).isEqualTo(2L)
        }

        @DisplayName("page / size 페이징이 적용된다.")
        @Test
        fun appliesPagination() {
            (1..5).forEach {
                persist(name = "P$it")
                Thread.sleep(5)
            }
            testEntityManager.clear()

            val page0 = productRepository.findAll(ProductSortType.LATEST, null, 0, 2)
            val page1 = productRepository.findAll(ProductSortType.LATEST, null, 1, 2)

            assertThat(page0).hasSize(2)
            assertThat(page1).hasSize(2)
            assertThat(page0[0].id).isNotEqualTo(page1[0].id)
        }
    }

    @DisplayName("findAllForAdmin")
    @Nested
    inner class FindAllForAdmin {
        @DisplayName("createdAt desc + soft delete 제외 + brandId 필터를 적용한다.")
        @Test
        fun adminListAppliesAllRules() {
            val a = persist(name = "A", brandId = 1L)
            Thread.sleep(10)
            val b = persist(name = "B", brandId = 1L)
            Thread.sleep(10)
            val c = persist(name = "C", brandId = 2L)
            b.softDelete()
            productRepository.save(b)
            testEntityManager.flush()
            testEntityManager.clear()

            val all = productRepository.findAllForAdmin(null, 0, 10)
            val brandOne = productRepository.findAllForAdmin(1L, 0, 10)

            assertThat(all.map { it.id }).containsExactly(c.id, a.id)
            assertThat(brandOne.map { it.id }).containsExactly(a.id)
        }
    }

    @DisplayName("existsByBrandIdAndName")
    @Nested
    inner class ExistsByBrandIdAndName {
        @DisplayName("soft-deleted 를 제외하고 판정한다.")
        @Test
        fun existsExcludesSoftDeleted() {
            val saved = persist(name = "X", brandId = 1L)
            assertThat(productRepository.existsByBrandIdAndName(1L, "X")).isTrue()

            saved.softDelete()
            productRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            assertThat(productRepository.existsByBrandIdAndName(1L, "X")).isFalse()
        }
    }

    @DisplayName("findAllByBrandId")
    @Nested
    inner class FindAllByBrandId {
        @DisplayName("해당 브랜드의 (soft-deleted 제외) 상품을 모두 반환한다.")
        @Test
        fun returnsLiveProductsOfBrand() {
            val a = persist(name = "B1-A", brandId = 1L)
            val b = persist(name = "B1-B", brandId = 1L)
            val c = persist(name = "B2-C", brandId = 2L)
            b.softDelete()
            productRepository.save(b)
            testEntityManager.flush()
            testEntityManager.clear()

            val result = productRepository.findAllByBrandId(1L)

            assertThat(result.map { it.id }).containsExactly(a.id)
        }
    }
}
