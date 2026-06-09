package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.brand.BrandRepository
import com.loopers.interfaces.api.brand.BrandV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1AdminApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val brandRepository: BrandRepository,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api-admin/v1/brands (관리자) 브랜드 목록 조회")
    @Nested
    inner class GetBrands {
        @DisplayName("관리자 인증으로 조회하면, 200 과 페이지 봉투를 최신순으로 받는다.")
        @Test
        fun returnsPageEnvelope_newestFirst() {
            brandRepository.save(BrandFixture.validBrand("A"))
            brandRepository.save(BrandFixture.validBrand("B"))

            val response = getBrands()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                // createdAt desc, id desc → 나중에 저장된 B 가 먼저.
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("B", "A") },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
                { assertThat(response.body?.data?.page).isEqualTo(0) },
            )
        }

        @DisplayName("등록된 브랜드가 없으면, 200 과 빈 content + 페이지 메타를 받는다.")
        @Test
        fun returnsEmpty_whenNone() {
            val response = getBrands()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("page / size 가 반영되고 totalElements / totalPages 가 정확하다.")
        @Test
        fun respectsPaging() {
            listOf("A", "B", "C").forEach { brandRepository.save(BrandFixture.validBrand(it)) }

            val response = getBrands(page = 0, size = 2)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(2) },
                { assertThat(response.body?.data?.size).isEqualTo(2) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(3L) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("X-Loopers-Ldap 헤더 없이 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun unauthorized_whenNoAdminHeader() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands",
                HttpMethod.GET,
                HttpEntity<Void>(HttpHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNAUTHORIZED") },
            )
        }
    }

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set("X-Loopers-Ldap", "loopers.admin")
    }

    private fun getBrands(page: Int? = null, size: Int? = null) =
        testRestTemplate.exchange(
            buildString {
                append("/api-admin/v1/brands")
                val params = listOfNotNull(page?.let { "page=$it" }, size?.let { "size=$it" })
                if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
            },
            HttpMethod.GET,
            HttpEntity<Void>(adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandsResponse>>() {},
        )
}
