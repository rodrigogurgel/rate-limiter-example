package br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.handler

import br.com.rodrigogurgel.ratelimiterexample.application.input.chain.Handler
import br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.RateLimitContext
import br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.RateLimitType
import br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.handler.exception.RateLimitRequestNotFoundException
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.RateLimitDatastoreOutputPort
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.response.RateLimitResponse

abstract class AbstractRateLimitHandler(
    private val rateLimitDatastoreOutputPort: RateLimitDatastoreOutputPort
) : Handler<RateLimitContext, RateLimitContext>() {
    abstract val type: RateLimitType

    override fun process(
        ctx: RateLimitContext,
        mapper: (RateLimitContext) -> RateLimitContext
    ): Step<RateLimitContext, RateLimitContext> {
        val request = ctx.getRequest() ?: throw RateLimitRequestNotFoundException(type)
        val response = rateLimitDatastoreOutputPort.acquirePermit(request)

        ctx.setResponse(
            response = response
        )

        if (!response.allowed) return Step.Stop(mapper(ctx))

        val token = response.toCompensationToken(1)
        val undo: (() -> Unit)? = token?.let { t -> { rateLimitDatastoreOutputPort.compensate(t) } }
        return Step.Next(ctx, undo)
    }

    private fun RateLimitContext.getRequest(): RateLimitRequest? {
        return this.requests[type]
    }

    private fun RateLimitContext.setResponse(response: RateLimitResponse) {
        this.responses[type] = response
    }
}