package com.loopers.interfaces.api.brand

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Brand Admin V1 API", description = "관리자 브랜드 API")
interface BrandV1AdminApiSpec {
    @Operation(
        summary = "(관리자) 브랜드 목록 조회",
        description = "삭제 마크되지 않은 브랜드를 최신순으로 페이지 단위 조회합니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun getBrands(pageable: Pageable): ApiResponse<BrandV1Dto.BrandsResponse>

    @Operation(
        summary = "(관리자) 브랜드 상세 조회",
        description = "브랜드 식별자로 단일 브랜드를 조회합니다. 삭제 마크된 브랜드는 찾을 수 없음으로 응답합니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun getBrand(brandId: Long): ApiResponse<BrandV1Dto.AdminBrandResponse>

    @Operation(
        summary = "(관리자) 브랜드 등록",
        description = "새 브랜드를 등록합니다. 이름이 비어 있으면 400, 기존 브랜드와 중복되면 409 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun registerBrand(request: BrandV1Dto.RegisterBrandRequest): ApiResponse<BrandV1Dto.AdminBrandResponse>
}
