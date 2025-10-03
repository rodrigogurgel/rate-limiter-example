package br.com.rodrigogurgel.ratelimiterexample.application.output.input.ratelimit

import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.response.RateLimitResponse

class RateLimitContext(
    val requests: RateLimitRequests,
    val responses: RateLimitResponses = RateLimitResponses()
) {
    data class RateLimitRequests(
        val account: RateLimitRequest,
        val product: RateLimitRequest,
    )

    data class RateLimitResponses(
        var account: RateLimitResponse? = null,
        var product: RateLimitResponse? = null,
    ) {
        fun isAllowed(): Boolean {
            return account?.allowed ?: false && product?.allowed ?: false
        }
    }
}