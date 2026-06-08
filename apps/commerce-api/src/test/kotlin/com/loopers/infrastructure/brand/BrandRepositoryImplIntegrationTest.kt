package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandRepository
import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.brand.BrandFixture
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
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, BrandRepositoryImpl::class)
class BrandRepositoryImplIntegrationTest @Autowired constructor(
    private val brandRepository: BrandRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val testEntityManager: TestEntityManager,
) {
    @DisplayName("save / findById 라운드트립")
    @Nested
    inner class SaveAndFind {
        @DisplayName("save 후 findById 로 동일 도메인 객체가 복원된다.")
        @Test
        fun saveThenFindById() {
            val saved = brandRepository.save(BrandFixture.validBrand("애플"))
            testEntityManager.flush()
            testEntityManager.clear()

            val found = brandRepository.findById(saved.id)

            assertThat(found).isNotNull()
            assertThat(found!!.name.value).isEqualTo("애플")
        }

        @DisplayName("findById 는 soft-deleted Brand 를 null 로 반환한다.")
        @Test
        fun findByIdExcludesSoftDeleted() {
            val saved = brandRepository.save(BrandFixture.validBrand())
            testEntityManager.flush()
            saved.softDelete()
            brandRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            val found = brandRepository.findById(saved.id)

            assertThat(found).isNull()
        }
    }

    @DisplayName("findAll(page, size)")
    @Nested
    inner class FindAll {
        @DisplayName("createdAt desc 정렬 + soft-deleted 제외 + 페이징이 적용된다.")
        @Test
        fun findAllOrdersAndExcludesAndPagesIn() {
            val first = brandRepository.save(BrandFixture.validBrand("A"))
            Thread.sleep(10)
            val second = brandRepository.save(BrandFixture.validBrand("B"))
            Thread.sleep(10)
            val third = brandRepository.save(BrandFixture.validBrand("C"))
            testEntityManager.flush()
            third.softDelete()
            brandRepository.save(third)
            testEntityManager.flush()
            testEntityManager.clear()

            val page0 = brandRepository.findAll(page = 0, size = 1)
            val page1 = brandRepository.findAll(page = 1, size = 1)

            assertThat(page0.map { it.name.value }).containsExactly("B")
            assertThat(page1.map { it.name.value }).containsExactly("A")
            assertThat(brandRepository.findAll(0, 10).map { it.name.value })
                .doesNotContain("C")
        }
    }

    @DisplayName("existsByName")
    @Nested
    inner class ExistsByName {
        @DisplayName("soft-deleted 를 제외하고 판정한다.")
        @Test
        fun existsByNameExcludesSoftDeleted() {
            val saved = brandRepository.save(BrandFixture.validBrand("애플"))
            testEntityManager.flush()
            assertThat(brandRepository.existsByName("애플")).isTrue()

            saved.softDelete()
            brandRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            assertThat(brandRepository.existsByName("애플")).isFalse()
        }
    }
}
