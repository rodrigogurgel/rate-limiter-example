package br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.handler

import br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.RateLimitType
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.RateLimitDatastoreOutputPort

class RateLimitProductHandler(
    rateLimitDatastoreOutputPort: RateLimitDatastoreOutputPort
) : AbstractRateLimitHandler(rateLimitDatastoreOutputPort) {
    override val type: RateLimitType
        get() = RateLimitType.PRODUCT
}