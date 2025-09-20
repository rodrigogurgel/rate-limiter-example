package br.com.rodrigogurgel.ratelimiterexample.application.output

import br.com.rodrigogurgel.ratelimiterexample.domain.vo.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.domain.vo.RateLimitResult

interface RedisDataStoreOutputPort {
    fun tryConsume(request: RateLimitRequest): RateLimitResult
}