package com.loopers.application.product

import com.loopers.application.brand.port.BrandRepository
import com.loopers.application.product.command.RegisterProductCommand
import com.loopers.application.product.command.UpdateProductCommand
import com.loopers.application.product.port.ProductRepository
import com.loopers.domain.brand.BrandErrorType
import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.SalesStatus
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("ProductFacade")
class ProductFacadeTest {
    private val productRepository: ProductRepository = mockk()
    private val brandRepository: BrandRepository = mockk()
    private val productFacade = ProductFacade(productRepository, brandRepository)

    @Nested
    @DisplayName("getProducts — UC-1 회원 카탈로그 목록")
    inner class GetProducts {
        @Test
        @DisplayName("sort=LATEST 로 조회하면 Repository 에 동일한 sort 가 전달된다")
        fun delegatesLatestSortToRepository() {
            every {
                productRepository.findAll(ProductSortType.LATEST, null, 0, 20)
            } returns listOf(ProductFixture.validProduct())

            val result = productFacade.getProducts(ProductSortType.LATEST, null, 0, 20)

            assertThat(result).hasSize(1)
            verify { productRepository.findAll(ProductSortType.LATEST, null, 0, 20) }
        }

        @Test
        @DisplayName("sort=PRICE_ASC 로 조회하면 Repository 에 동일한 sort 가 전달된다")
        fun delegatesPriceAscSortToRepository() {
            every {
                productRepository.findAll(ProductSortType.PRICE_ASC, null, 0, 20)
            } returns emptyList()

            productFacade.getProducts(ProductSortType.PRICE_ASC, null, 0, 20)

            verify { productRepository.findAll(ProductSortType.PRICE_ASC, null, 0, 20) }
        }

        @Test
        @DisplayName("sort=LIKES_DESC 로 조회하면 Repository 에 동일한 sort 가 전달된다")
        fun delegatesLikesDescSortToRepository() {
            every {
                productRepository.findAll(ProductSortType.LIKES_DESC, null, 0, 20)
            } returns emptyList()

            productFacade.getProducts(ProductSortType.LIKES_DESC, null, 0, 20)

            verify { productRepository.findAll(ProductSortType.LIKES_DESC, null, 0, 20) }
        }

        @Test
        @DisplayName("sort 가 null 이면 LATEST 로 해석되어 Repository 에 전달된다")
        fun nullSortResolvesToLatest() {
            every {
                productRepository.findAll(ProductSortType.LATEST, null, 0, 20)
            } returns emptyList()

            productFacade.getProducts(sort = null, brandId = null, page = 0, size = 20)

            verify { productRepository.findAll(ProductSortType.LATEST, null, 0, 20) }
        }

        @Test
        @DisplayName("brandId 필터를 지정하면 Repository 에 동일한 brandId 가 전달된다")
        fun delegatesBrandIdFilterToRepository() {
            val brandId = 42L
            every {
                productRepository.findAll(ProductSortType.LATEST, brandId, 0, 20)
            } returns emptyList()

            productFacade.getProducts(ProductSortType.LATEST, brandId, 0, 20)

            verify { productRepository.findAll(ProductSortType.LATEST, brandId, 0, 20) }
        }

        @Test
        @DisplayName("page / size 가 Repository 에 그대로 전달된다")
        fun delegatesPagingToRepository() {
            every {
                productRepository.findAll(ProductSortType.LATEST, null, 3, 50)
            } returns emptyList()

            productFacade.getProducts(ProductSortType.LATEST, null, 3, 50)

            verify { productRepository.findAll(ProductSortType.LATEST, null, 3, 50) }
        }
    }

    @Nested
    @DisplayName("getProductDetail — UC-2 회원 상세")
    inner class GetProductDetail {
        @Test
        @DisplayName("존재하는 productId 로 호출하면 ProductDetailResult 가 반환된다 (비회원: likedByMe=false)")
        fun returnsProductDetailWithBrand() {
            val product = ProductFixture.validProduct()
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand

            val result = productFacade.getProductDetail(productId = 1L, loginId = null)

            assertThat(result.name).isEqualTo(product.name.value)
            assertThat(result.price).isEqualTo(product.price.value)
            assertThat(result.brandName).isEqualTo(brand.name)
            assertThat(result.likedByMe).isFalse()
        }

        @Test
        @DisplayName("존재하지 않는 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenProductMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> {
                productFacade.getProductDetail(productId = 99L, loginId = null)
            }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
        }

        @Test
        @DisplayName("product 는 존재하나 brand 가 없으면 BRAND_NOT_FOUND 예외가 발생한다 (정합성)")
        fun throwsWhenBrandMissing() {
            val product = ProductFixture.validProduct()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns null

            val ex = assertThrows<CoreException> {
                productFacade.getProductDetail(productId = 1L, loginId = null)
            }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.BRAND_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("getProductsForAdmin — UC-9 관리자 목록")
    inner class GetProductsForAdmin {
        @Test
        @DisplayName("Repository 의 findAllForAdmin 으로 최신순 페이징을 위임한다")
        fun delegatesToFindAllForAdmin() {
            every { productRepository.findAllForAdmin(null, 0, 20) } returns emptyList()

            productFacade.getProductsForAdmin(brandId = null, page = 0, size = 20)

            verify { productRepository.findAllForAdmin(null, 0, 20) }
        }

        @Test
        @DisplayName("brandId 필터를 Repository 에 전달한다")
        fun delegatesBrandIdFilter() {
            every { productRepository.findAllForAdmin(7L, 1, 30) } returns emptyList()

            productFacade.getProductsForAdmin(brandId = 7L, page = 1, size = 30)

            verify { productRepository.findAllForAdmin(7L, 1, 30) }
        }

        @Test
        @DisplayName("응답 항목에 salesStatus 가 포함된다")
        fun responseIncludesSalesStatus() {
            val product = ProductFixture.validProduct()
            every { productRepository.findAllForAdmin(null, 0, 20) } returns listOf(product)

            val result = productFacade.getProductsForAdmin(brandId = null, page = 0, size = 20)

            assertThat(result).hasSize(1)
            assertThat(result[0].salesStatus).isEqualTo(SalesStatus.ON_SALE)
        }
    }

    @Nested
    @DisplayName("getProductForAdmin — UC-10 관리자 상세")
    inner class GetProductForAdmin {
        @Test
        @DisplayName("존재하는 productId 로 호출하면 AdminProductDetailResult 가 반환된다")
        fun returnsAdminProductDetail() {
            val product = ProductFixture.validProduct()
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand

            val result = productFacade.getProductForAdmin(productId = 1L)

            assertThat(result.brandName).isEqualTo(brand.name)
            assertThat(result.salesStatus).isEqualTo(SalesStatus.ON_SALE)
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { productFacade.getProductForAdmin(99L) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("registerProduct — UC-11 관리자 등록")
    inner class RegisterProduct {
        private val command = RegisterProductCommand(
            brandId = 1L,
            name = "갤럭시 S25",
            price = 1_300_000,
            stock = 50,
        )

        @Test
        @DisplayName("유효한 입력으로 등록하면 salesStatus=ON_SALE 의 신규 Product 가 저장된다")
        fun registersProductWithOnSale() {
            val brand = BrandFixture.validBrand()
            val saved = slot<Product>()
            every { brandRepository.findById(1L) } returns brand
            every { productRepository.existsByBrandIdAndName(1L, command.name) } returns false
            every { productRepository.save(capture(saved)) } answers { saved.captured }

            productFacade.registerProduct(command)

            assertThat(saved.captured.name.value).isEqualTo(command.name)
            assertThat(saved.captured.price.value).isEqualTo(command.price)
            assertThat(saved.captured.brandId).isEqualTo(command.brandId)
            assertThat(saved.captured.salesStatus).isEqualTo(SalesStatus.ON_SALE)
            verify { productRepository.save(any()) }
        }

        @Test
        @DisplayName("지정 brandId 가 존재하지 않으면 BRAND_NOT_FOUND 예외가 발생한다")
        fun throwsWhenBrandMissing() {
            every { brandRepository.findById(1L) } returns null

            val ex = assertThrows<CoreException> { productFacade.registerProduct(command) }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.BRAND_NOT_FOUND)
            verify(exactly = 0) { productRepository.save(any()) }
        }

        @Test
        @DisplayName("같은 브랜드 안에 같은 이름이 이미 있으면 DUPLICATE_PRODUCT_NAME 예외가 발생한다")
        fun throwsWhenDuplicateName() {
            every { brandRepository.findById(1L) } returns BrandFixture.validBrand()
            every { productRepository.existsByBrandIdAndName(1L, command.name) } returns true

            val ex = assertThrows<CoreException> { productFacade.registerProduct(command) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.DUPLICATE_PRODUCT_NAME)
            verify(exactly = 0) { productRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("updateProduct — UC-12 관리자 수정")
    inner class UpdateProduct {
        @Test
        @DisplayName("정상 입력으로 수정하면 name / price / salesStatus 가 갱신된다")
        fun updatesNamePriceSalesStatus() {
            val product = ProductFixture.validProduct()
            val command = UpdateProductCommand(
                name = "맥북 프로 16인치",
                price = 3_300_000,
                salesStatus = SalesStatus.OFF_SALE,
            )
            every { productRepository.findById(1L) } returns product
            every { productRepository.existsByBrandIdAndName(product.brandId, command.name) } returns false
            every { brandRepository.findById(product.brandId) } returns BrandFixture.validBrand()
            every { productRepository.save(product) } returns product

            productFacade.updateProduct(productId = 1L, command = command)

            assertThat(product.name.value).isEqualTo(command.name)
            assertThat(product.price.value).isEqualTo(command.price)
            assertThat(product.salesStatus).isEqualTo(SalesStatus.OFF_SALE)
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            val command = UpdateProductCommand("x", 1, SalesStatus.ON_SALE)
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { productFacade.updateProduct(99L, command) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
        }

        @Test
        @DisplayName("같은 브랜드에 같은 이름의 다른 상품이 있으면 DUPLICATE_PRODUCT_NAME 예외가 발생한다 (자기 자신 제외)")
        fun throwsWhenAnotherWithSameName() {
            val product = ProductFixture.validProduct()
            val command = UpdateProductCommand(
                name = "갤럭시 S25",
                price = 1_300_000,
                salesStatus = SalesStatus.ON_SALE,
            )
            every { productRepository.findById(1L) } returns product
            every { productRepository.existsByBrandIdAndName(product.brandId, command.name) } returns true

            val ex = assertThrows<CoreException> { productFacade.updateProduct(1L, command) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.DUPLICATE_PRODUCT_NAME)
        }

        @Test
        @DisplayName("이름이 동일하면 (자기 자신) 중복 검사를 호출하지 않는다")
        fun skipsDuplicateCheckWhenSameName() {
            val product = ProductFixture.validProduct()
            val command = UpdateProductCommand(
                name = product.name.value,
                price = 999,
                salesStatus = SalesStatus.OUT_OF_STOCK,
            )
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns BrandFixture.validBrand()
            every { productRepository.save(product) } returns product

            productFacade.updateProduct(1L, command)

            verify(exactly = 0) { productRepository.existsByBrandIdAndName(any(), any()) }
        }
    }

    @Nested
    @DisplayName("deleteProduct — UC-13 관리자 삭제")
    inner class DeleteProduct {
        @Test
        @DisplayName("정상 호출 시 Product 가 soft delete 된 채로 저장된다")
        fun softDeletesProduct() {
            val product = ProductFixture.validProduct()
            every { productRepository.findById(1L) } returns product
            every { productRepository.save(product) } returns product

            productFacade.deleteProduct(productId = 1L)

            assertThat(product.isDeleted()).isTrue()
            verify { productRepository.save(product) }
        }

        @Test
        @DisplayName("존재하지 않거나 이미 삭제된 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { productFacade.deleteProduct(99L) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
            verify(exactly = 0) { productRepository.save(any()) }
        }
    }
}
