package br.com.rodrigogurgel.ratelimiterexample.framework.config.redis

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.script.DefaultRedisScript

@Configuration
class RedisConfig {
    @Bean
    fun rateLimiterScript(): DefaultRedisScript<List<Long>> {
        val script = DefaultRedisScript<List<Long>>()
        script.setLocation(ClassPathResource("scripts/rate_limiter_try_consume.lua"))
        script.resultType = List::class.java as Class<List<Long>>
        return script
    }
}