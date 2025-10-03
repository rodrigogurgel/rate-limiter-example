package br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit

import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.response.RateLimitResponse

interface RedisDatastoreOutputPort {
    fun acquirePermit(request: RateLimitRequest): RateLimitResponse
    fun compensate(token: RateLimitResponse.CompensationToken)
}