package br.com.rodrigogurgel.ratelimiterexample.framework.adapter.output.datastore.redis

import br.com.rodrigogurgel.ratelimiterexample.application.output.RedisDataStoreOutputPort
import br.com.rodrigogurgel.ratelimiterexample.domain.vo.RateLimitResult
import br.com.rodrigogurgel.ratelimiterexample.framework.filter.ReactiveRateLimitingFilter
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service

@Service
class RateLimiterDatastoreOutputPort(
    private val redisTemplate: RedisTemplate<String, String>,
    private val rateLimiterScript: DefaultRedisScript<List<Long>>
) : RedisDataStoreOutputPort {

    companion object {
        private const val ALLOWED_VALUE_POSITION = 0
        private const val REMAINING_VALUE_POSITION = 1
        private const val TTL_VALUE_POSITION = 2

        private val logger = LoggerFactory.getLogger(ReactiveRateLimitingFilter::class.java)
    }

    override fun tryConsume(key: String, limit: Long, windowMs: Long): RateLimitResult = runCatching {
        val result = redisTemplate.execute(
            rateLimiterScript,
            listOf(key),
            limit.toString(),
            windowMs.toString()
        )

        if (result.size < 3) {
            throw IllegalStateException("Erro ao executar script de rate limit")
        }

        val allowed = result[ALLOWED_VALUE_POSITION] == 1L
        val remaining = result[REMAINING_VALUE_POSITION]
        val ttl = result[TTL_VALUE_POSITION]

        return RateLimitResult(allowed, remaining, ttl)
    }.onFailure {
        logger.error("Erro ao executar script de rate limit", it)
    }.getOrElse {
        return RateLimitResult(
            allowed = false,
            remaining = 0L,
            ttlMs = 0L
        )
    }
}