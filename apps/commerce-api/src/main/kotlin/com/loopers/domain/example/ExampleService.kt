package com.loopers.domain.example

import com.loopers.support.error.CommonErrorType
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ExampleService(
    private val exampleRepository: ExampleRepository,
) {
    @Transactional(readOnly = true)
    fun getExample(id: Long): ExampleModel {
        return exampleRepository.find(id)
            ?: throw CoreException(errorType = CommonErrorType.NOT_FOUND, customMessage = "[id = $id] 예시를 찾을 수 없습니다.")
    }
}
