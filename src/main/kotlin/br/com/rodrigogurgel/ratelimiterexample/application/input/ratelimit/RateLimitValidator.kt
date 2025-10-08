package br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit

import br.com.rodrigogurgel.ratelimiterexample.application.input.chain.Handler
import br.com.rodrigogurgel.ratelimiterexample.application.output.metrics.RateLimitMetricsOutputPort
import br.com.rodrigogurgel.ratelimiterexample.common.logger.extensions.rateLimiterSuccessfully
import org.slf4j.LoggerFactory

class RateLimitValidator(
    private val primaryHandler: Handler<RateLimitContext, RateLimitContext>,
    private val rateLimitMetricsOutputPort: RateLimitMetricsOutputPort
) {

    companion object {
        private val logger = LoggerFactory.getLogger(RateLimitValidator::class.java)
    }

    fun validate(rateLimitContext: RateLimitContext): RateLimitContext {
        val ctx = primaryHandler.handle(rateLimitContext) {
            it
        }

        rateLimitMetricsOutputPort.recordHit(
            allowed = ctx.responses.map { it.value.allowed }.all { it },
            account = ctx.requests[RateLimitType.ACCOUNT]?.key.orEmpty(),
            product = ctx.requests[RateLimitType.PRODUCT]?.key.orEmpty(),
        )

        logger.rateLimiterSuccessfully(
            allowed = ctx.responses.map { it.value.allowed }.all { it },
            account = ctx.requests[RateLimitType.ACCOUNT]?.key.orEmpty(),
            product = ctx.requests[RateLimitType.PRODUCT]?.key.orEmpty(),
        )

        return ctx
    }
}