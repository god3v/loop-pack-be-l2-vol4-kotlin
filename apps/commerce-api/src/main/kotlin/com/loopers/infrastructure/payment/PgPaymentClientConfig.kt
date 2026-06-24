package com.loopers.infrastructure.payment

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class PgPaymentClientConfig {
    @Bean
    fun pgRestClient(
        builder: RestClient.Builder,
        @Value("\${pg-simulator.base-url:http://localhost:8082}") baseUrl: String,
    ): RestClient = builder.baseUrl(baseUrl).build()
}
