package br.com.rodrigogurgel.ratelimiterexample.application.output

import br.com.rodrigogurgel.ratelimiterexample.domain.vo.RateLimitResult

interface RedisDataStoreOutputPort {
    fun tryConsume(key: String, limit: Long, windowMs: Long): RateLimitResult
}