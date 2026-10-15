package com.mentalgym.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.OncePerRequestFilter

class OriginValidationFilter(
    private val allowedOrigins: Set<String>,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val origin = request.getHeader("Origin")
        if (origin != null && origin !in allowedOrigins) {
            log.warn("Rejected request to {} from origin {}", request.requestURI, origin)
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin not allowed")
            return
        }
        filterChain.doFilter(request, response)
    }
}

@Configuration
class OriginValidationConfig {

    @Bean
    fun originValidationFilterRegistration(
        properties: MentalGymProperties,
    ): FilterRegistrationBean<OriginValidationFilter> =
        FilterRegistrationBean(OriginValidationFilter(properties.allowedOrigins.toSet())).apply {
            addUrlPatterns("${properties.mcpEndpoint}/*", properties.mcpEndpoint)
            order = Int.MIN_VALUE
        }
}
