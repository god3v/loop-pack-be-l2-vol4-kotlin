package com.loopers.interfaces.api.brand

import com.loopers.application.brand.result.BrandResult

class BrandV1Dto {
    data class BrandResponse(
        val id: Long,
        val name: String,
    ) {
        companion object {
            fun from(result: BrandResult): BrandResponse = BrandResponse(id = result.id, name = result.name)
        }
    }
}
