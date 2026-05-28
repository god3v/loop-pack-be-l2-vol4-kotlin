package com.loopers.infrastructure.brand

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.Brand
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "brands")
@SQLRestriction("deleted_at IS NULL")
class BrandEntity private constructor(
    name: String,
) : BaseEntity() {
    var name: String = name
        protected set

    fun toDomain(): Brand = Brand(id = this.id, name = this.name)

    fun syncFrom(brand: Brand) {
        this.name = brand.name
        if (brand.isDeleted() && this.deletedAt == null) {
            this.delete()
        }
    }

    companion object {
        fun from(brand: Brand): BrandEntity = BrandEntity(name = brand.name)
    }
}
