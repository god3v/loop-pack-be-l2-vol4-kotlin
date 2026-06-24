package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class PaymentTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 6, 12, 10, 0)

    private fun requested() = Payment.request(orderId = 1L, amount = 1000L)

    @DisplayName("request 로 생성하면, ")
    @Nested
    inner class Request {
        @DisplayName("REQUESTED 상태로 주문·금액을 보관하고 결제/취소 메타는 비어 있다.")
        @Test
        fun createsRequested() {
            val payment = requested()

            assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED)
            assertThat(payment.orderId).isEqualTo(1L)
            assertThat(payment.amount).isEqualTo(1000L)
            assertThat(payment.transactionId).isNull()
            assertThat(payment.failureReason).isNull()
            assertThat(payment.paidAt).isNull()
            assertThat(payment.canceledAt).isNull()
        }
    }

    @DisplayName("accept 는, ")
    @Nested
    inner class Accept {
        @DisplayName("REQUESTED 결제에 외부 거래 식별자를 접수 기록하고 상태는 REQUESTED 로 유지한다.")
        @Test
        fun recordsTransactionKeyWhileRequested() {
            val payment = requested()

            payment.accept(transactionId = "20260623:TR:9577c5")

            assertThat(payment.transactionId).isEqualTo("20260623:TR:9577c5")
            assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED)
        }
    }

    @DisplayName("approve 는, ")
    @Nested
    inner class Approve {
        @DisplayName("REQUESTED 를 APPROVED 로 전이하고 거래 식별자·결제시각을 기록한다.")
        @Test
        fun approvesRequested() {
            val payment = requested()

            payment.approve(transactionId = "tx-1", at = now)

            assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(payment.transactionId).isEqualTo("tx-1")
            assertThat(payment.paidAt).isEqualTo(now)
        }

        @DisplayName("이미 APPROVED 인 결제를 다시 승인하면 INVALID_PAYMENT_TRANSITION 으로 막힌다.")
        @Test
        fun rejectsWhenAlreadyApproved() {
            val payment = requested().also { it.approve("tx-1", now) }

            val ex = assertThrows<CoreException> { payment.approve("tx-2", now) }

            assertThat(ex.errorType).isEqualTo(PaymentErrorType.INVALID_PAYMENT_TRANSITION)
        }

        @DisplayName("FAILED 인 결제는 승인으로 되돌릴 수 없다.")
        @Test
        fun rejectsWhenFailed() {
            val payment = requested().also { it.fail("DECLINED") }

            val ex = assertThrows<CoreException> { payment.approve("tx-1", now) }

            assertThat(ex.errorType).isEqualTo(PaymentErrorType.INVALID_PAYMENT_TRANSITION)
        }
    }

    @DisplayName("fail 은, ")
    @Nested
    inner class Fail {
        @DisplayName("REQUESTED 를 FAILED 로 전이하고 결과코드를 기록한다.")
        @Test
        fun failsRequested() {
            val payment = requested()

            payment.fail("DECLINED")

            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureReason).isEqualTo("DECLINED")
        }

        @DisplayName("이미 FAILED 면 멱등하게 통과한다 (중복 콜백 방어).")
        @Test
        fun idempotentWhenAlreadyFailed() {
            val payment = requested().also { it.fail("DECLINED") }

            payment.fail("DECLINED")

            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        }

        @DisplayName("APPROVED 인 결제를 실패로 뒤집을 수 없다.")
        @Test
        fun rejectsWhenApproved() {
            val payment = requested().also { it.approve("tx-1", now) }

            val ex = assertThrows<CoreException> { payment.fail("DECLINED") }

            assertThat(ex.errorType).isEqualTo(PaymentErrorType.INVALID_PAYMENT_TRANSITION)
        }
    }

    @DisplayName("cancel 은, ")
    @Nested
    inner class Cancel {
        @DisplayName("APPROVED 결제를 CANCELED 로 전이하고 취소시각을 기록한다 (환불).")
        @Test
        fun cancelsApproved() {
            val payment = requested().also { it.approve("tx-1", now) }

            payment.cancel(now)

            assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED)
            assertThat(payment.canceledAt).isEqualTo(now)
        }

        @DisplayName("아직 청구 전인 REQUESTED 도 CANCELED 로 전이한다 (환불 불필요).")
        @Test
        fun cancelsRequested() {
            val payment = requested()

            payment.cancel(now)

            assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED)
        }

        @DisplayName("이미 CANCELED 면 멱등하게 통과한다.")
        @Test
        fun idempotentWhenAlreadyCanceled() {
            val payment = requested().also { it.cancel(now) }

            payment.cancel(now)

            assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED)
        }

        @DisplayName("FAILED 인 결제는 취소할 수 없다.")
        @Test
        fun rejectsWhenFailed() {
            val payment = requested().also { it.fail("DECLINED") }

            val ex = assertThrows<CoreException> { payment.cancel(now) }

            assertThat(ex.errorType).isEqualTo(PaymentErrorType.INVALID_PAYMENT_TRANSITION)
        }
    }
}
