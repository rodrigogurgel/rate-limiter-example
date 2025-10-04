package br.com.rodrigogurgel.ratelimiterexample.application.output.input.ratelimit

import br.com.rodrigogurgel.ratelimiterexample.application.output.chain.Handler
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
            allowed = ctx.responses.isAllowed(),
            account = ctx.requests.account.key,
            product = ctx.requests.product.key,
        )

        logger.rateLimiterSuccessfully(
            allowed = ctx.responses.isAllowed(),
            account = ctx.requests.account.key,
            product = ctx.requests.product.key,
        )

        return ctx
    }
}