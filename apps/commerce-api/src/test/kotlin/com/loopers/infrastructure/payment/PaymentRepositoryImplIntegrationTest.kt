package com.loopers.infrastructure.payment

import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, PaymentRepositoryImpl::class)
class PaymentRepositoryImplIntegrationTest @Autowired constructor(
    private val paymentRepository: PaymentRepository,
    private val testEntityManager: TestEntityManager,
) {
    private fun persistRequested(orderId: Long = 1L, amount: Long = 1000L): Payment {
        val saved = paymentRepository.save(Payment.request(orderId = orderId, amount = amount))
        testEntityManager.flush()
        return saved
    }

    @DisplayName("save 후 findById 로 REQUESTED 결제가 복원된다.")
    @Test
    fun saveThenFindByIdRequested() {
        val saved = persistRequested(orderId = 7L, amount = 5000L)
        testEntityManager.clear()

        val found = paymentRepository.findById(saved.id)

        assertThat(found).isNotNull()
        val payment = requireNotNull(found)
        assertThat(payment.orderId).isEqualTo(7L)
        assertThat(payment.amount).isEqualTo(5000L)
        assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED)
        assertThat(payment.transactionId).isNull()
        assertThat(payment.failureReason).isNull()
        assertThat(payment.paidAt).isNull()
    }

    @DisplayName("승인 후 저장하면 APPROVED·거래식별자·결제시각이 영속된다.")
    @Test
    fun persistsApprovedState() {
        val saved = persistRequested()
        testEntityManager.clear()
        val at = LocalDateTime.of(2026, 6, 12, 10, 0, 0)

        val payment = requireNotNull(paymentRepository.findById(saved.id)).also { it.approve("tx-1", at) }
        paymentRepository.save(payment)
        testEntityManager.flush()
        testEntityManager.clear()

        val found = requireNotNull(paymentRepository.findById(saved.id))
        assertThat(found.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(found.transactionId).isEqualTo("tx-1")
        assertThat(found.paidAt).isEqualTo(at)
    }

    @DisplayName("실패 후 저장하면 FAILED·failureReason 이 영속된다.")
    @Test
    fun persistsFailedState() {
        val saved = persistRequested()
        testEntityManager.clear()

        val payment = requireNotNull(paymentRepository.findById(saved.id)).also { it.fail("DECLINED") }
        paymentRepository.save(payment)
        testEntityManager.flush()
        testEntityManager.clear()

        val found = requireNotNull(paymentRepository.findById(saved.id))
        assertThat(found.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(found.failureReason).isEqualTo("DECLINED")
    }

    @DisplayName("findByIdForUpdate 는 비관 락으로 동일 결제를 조회한다.")
    @Test
    fun findByIdForUpdateReturnsRow() {
        val saved = persistRequested()
        testEntityManager.clear()

        val found = paymentRepository.findByIdForUpdate(saved.id)

        assertThat(found).isNotNull()
        val payment = requireNotNull(found)
        assertThat(payment.id).isEqualTo(saved.id)
        assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED)
    }
}
