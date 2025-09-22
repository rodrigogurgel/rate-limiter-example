package br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit

import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.response.RateLimitResponse

interface RedisDatastoreOutputPort {
    fun tryConsume(request: RateLimitRequest): RateLimitResponse
}