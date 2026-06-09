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
}
