package br.com.rodrigogurgel.ratelimiterexample.framework.adapter.output.datastore.redis

import br.com.rodrigogurgel.ratelimiterexample.application.output.RedisDataStoreOutputPort
import br.com.rodrigogurgel.ratelimiterexample.domain.vo.RateLimitRequest
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
        private val logger = LoggerFactory.getLogger(ReactiveRateLimitingFilter::class.java)
    }

    override fun tryConsume(request: RateLimitRequest): RateLimitResult = runCatching {
        val result = redisTemplate.execute(
            rateLimiterScript,
            request.keys,
            *request.asArgs()
        )

        return RateLimitResult(result)
    }.onFailure {
        logger.error("Erro ao executar script de rate limit", it)
    }.getOrElse {
        return if (request.allowOnError) RateLimitResult.defaultAllowed(request)
        else RateLimitResult.defaultDisallowed(request)
    }
}