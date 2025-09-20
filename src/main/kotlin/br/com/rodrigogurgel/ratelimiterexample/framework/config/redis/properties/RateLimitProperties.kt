package br.com.rodrigogurgel.ratelimiterexample.framework.config.redis.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ratelimit")
data class RateLimitProperties(
    val allowOnError: Boolean,
    val account: Params,
    val product: Params,
    val excludePatterns: List<String> = listOf("/actuator/health")
) {
    data class Params(
        val enabled: Boolean,
        val limit: Long = 60,
        val windowMs: Long = 1000,
    )
}
