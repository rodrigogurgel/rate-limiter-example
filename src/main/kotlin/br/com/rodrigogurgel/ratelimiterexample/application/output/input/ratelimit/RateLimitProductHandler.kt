package br.com.rodrigogurgel.ratelimiterexample.application.output.input.ratelimit

import br.com.rodrigogurgel.ratelimiterexample.application.output.chain.Handler
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.RedisDatastoreOutputPort

class RateLimitProductHandler(private val redisDatastoreOutputPort: RedisDatastoreOutputPort) :
    Handler<RateLimitContext, RateLimitContext>() {

    override fun process(ctx: RateLimitContext): Step<RateLimitContext, RateLimitContext> {
        val request = ctx.requests.product
        val response = redisDatastoreOutputPort.acquirePermit(request)
        ctx.responses.product = response

        if (!response.allowed) return Step.Stop(ctx)

        val token = response.toCompensationToken(1)
        val undo: (() -> Unit)? = token?.let { t -> { redisDatastoreOutputPort.compensate(t) } }
        return Step.Next(ctx, undo)
    }
}