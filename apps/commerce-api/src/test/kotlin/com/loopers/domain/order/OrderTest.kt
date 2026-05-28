package com.loopers.domain.order

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZoneId

class OrderTest {
    private fun line(productId: Long = 1L, price: Int = 1000, qty: Int = 1) =
        OrderLine.create(productId = productId, productName = "P-$productId", unitPrice = price, quantity = qty)

    @DisplayName("Order 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("정상 인자로 생성하면 userId · lines · idempotencyKey · couponId 가 보관된다.")
        @Test
        fun preservesFields() {
            val lines = listOf(line(productId = 1L), line(productId = 2L))

            val order = Order.create(userId = 42L, lines = lines, idempotencyKey = "abc", couponId = 77L)

            assertThat(order.userId).isEqualTo(42L)
            assertThat(order.lines).hasSize(2)
            assertThat(order.idempotencyKey).isEqualTo("abc")
            assertThat(order.couponId).isEqualTo(77L)
        }

        @DisplayName("lines 가 비어 있으면 EMPTY_LINES 예외가 발생한다.")
        @Test
        fun throwsEmptyLines() {
            val ex = assertThrows<CoreException> {
                Order.create(userId = 42L, lines = emptyList(), idempotencyKey = "abc")
            }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.EMPTY_LINES)
        }

        @DisplayName("idempotencyKey 가 blank 이면 IDEMPOTENCY_KEY_BLANK 예외가 발생한다.")
        @Test
        fun throwsWhenIdempotencyKeyBlank() {
            val ex = assertThrows<CoreException> {
                Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "")
            }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.IDEMPOTENCY_KEY_BLANK)
        }

        @DisplayName("totalAmount 는 라인 subtotal 의 합과 같다.")
        @Test
        fun totalAmountEqualsSumOfSubtotals() {
            val lines = listOf(
                OrderLine.create(productId = 1L, productName = "T", unitPrice = 1000, quantity = 2),
                OrderLine.create(productId = 2L, productName = "Cap", unitPrice = 500, quantity = 3),
            )

            val order = Order.create(userId = 42L, lines = lines, idempotencyKey = "abc")

            assertThat(order.totalAmount).isEqualTo(2000 + 1500)
        }

        @DisplayName("orderedAt 이 생성 시점에 박힌다 (Asia/Seoul).")
        @Test
        fun orderedAtIsStampedAtCreation() {
            val seoul = ZoneId.of("Asia/Seoul")
            val before = LocalDateTime.now(seoul)

            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")

            val after = LocalDateTime.now(seoul)
            assertThat(order.orderedAt).isBetween(before, after)
        }

        @DisplayName("초기 status 는 PAYMENT_FAILED 다 (markPaid/markPaymentFailed 호출 전).")
        @Test
        fun initialStatusIsPaymentFailed() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")

            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        }
    }

    @DisplayName("markPaid / markPaymentFailed")
    @Nested
    inner class StateTransition {
        @DisplayName("markPaid 호출 시 status=PAID + transactionId/resultCode 가 박힌다.")
        @Test
        fun markPaidUpdatesState() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")

            order.markPaid(transactionId = "tx-1", resultCode = "APPROVED")

            assertThat(order.status).isEqualTo(OrderStatus.PAID)
            assertThat(order.paymentTransactionId).isEqualTo("tx-1")
            assertThat(order.paymentResultCode).isEqualTo("APPROVED")
        }

        @DisplayName("markPaymentFailed 호출 시 status=PAYMENT_FAILED + 메타가 박힌다 (null 도 허용).")
        @Test
        fun markPaymentFailedUpdatesState() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")

            order.markPaymentFailed(transactionId = null, resultCode = "DECLINED")

            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            assertThat(order.paymentTransactionId).isNull()
            assertThat(order.paymentResultCode).isEqualTo("DECLINED")
        }
    }
}
