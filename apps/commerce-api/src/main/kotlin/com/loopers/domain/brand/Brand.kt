package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import java.time.LocalDateTime

class Brand internal constructor(
    val id: Long = 0L,
    name: String,
) {
    var name: String = name
        private set

    var deletedAt: LocalDateTime? = null
        private set

    init {
        if (name.isBlank()) {
            throw CoreException(BrandErrorType.BRAND_BAD_REQUEST, "name 은 비어 있을 수 없다.")
        }
    }

    fun softDelete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now()
        }
    }

    fun isDeleted(): Boolean = deletedAt != null

    fun update(name: String) {
        this.name = name
    }

    companion object {
        fun create(name: String): Brand = Brand(name = name)
    }
}
