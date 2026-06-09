package com.loopers.interfaces.api.config

import com.loopers.interfaces.api.auth.AdminAuthInterceptor
import com.loopers.interfaces.api.auth.AuthInterceptor
import com.loopers.interfaces.api.auth.LoginUserArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val authInterceptor: AuthInterceptor,
    private val adminAuthInterceptor: AdminAuthInterceptor,
    private val loginUserArgumentResolver: LoginUserArgumentResolver,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor)
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api-admin/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(loginUserArgumentResolver)
    }
}
