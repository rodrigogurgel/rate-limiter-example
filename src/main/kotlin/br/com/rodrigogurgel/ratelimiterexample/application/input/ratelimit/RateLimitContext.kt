package br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit

import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.response.RateLimitResponse

class RateLimitContext(
    val requests: Map<RateLimitType, RateLimitRequest>,
    val responses: MutableMap<RateLimitType, RateLimitResponse> = mutableMapOf(),
)
