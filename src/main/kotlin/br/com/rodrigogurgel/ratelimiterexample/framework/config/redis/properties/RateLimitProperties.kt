package br.com.rodrigogurgel.ratelimiterexample.framework.config.redis.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ratelimit")
data class RateLimitProperties(
    val limit: Long = 60,
    val windowMs: Long = 1000,
    val excludePatterns: List<String> = listOf("/actuator/health")
)
