package com.loopers.infrastructure.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductPrice
import com.loopers.domain.product.SalesStatus
import com.loopers.domain.product.Stock
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
class ProductEntity private constructor(
    name: String,
    price: Int,
    stock: Int,
    likeCount: Long,
    brandId: Long,
    salesStatus: SalesStatus,
) : BaseEntity() {
    var name: String = name
        protected set

    var price: Int = price
        protected set

    var stock: Int = stock
        protected set

    @Column(name = "like_count")
    var likeCount: Long = likeCount
        protected set

    @Column(name = "brand_id")
    var brandId: Long = brandId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_status")
    var salesStatus: SalesStatus = salesStatus
        protected set

    fun toDomain(): Product = Product(
        id = this.id,
        name = ProductName.of(this.name),
        price = ProductPrice.of(this.price),
        stock = Stock.of(this.stock),
        likeCount = this.likeCount,
        brandId = this.brandId,
        salesStatus = this.salesStatus,
    )

    fun syncFrom(product: Product) {
        this.name = product.name.value
        this.price = product.price.value
        this.stock = product.stock.value
        this.salesStatus = product.salesStatus
        if (product.isDeleted() && this.deletedAt == null) {
            this.delete()
        }
    }

    companion object {
        fun from(product: Product): ProductEntity = ProductEntity(
            name = product.name.value,
            price = product.price.value,
            stock = product.stock.value,
            likeCount = product.likeCount,
            brandId = product.brandId,
            salesStatus = product.salesStatus,
        )
    }
}
