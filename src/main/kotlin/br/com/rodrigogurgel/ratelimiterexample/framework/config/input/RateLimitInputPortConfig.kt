package br.com.rodrigogurgel.ratelimiterexample.framework.config.input

import br.com.rodrigogurgel.ratelimiterexample.application.input.chain.Handler
import br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.handler.RateLimitAccountHandler
import br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.RateLimitContext
import br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.handler.RateLimitProductHandler
import br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.RateLimitValidator
import br.com.rodrigogurgel.ratelimiterexample.application.output.metrics.RateLimitMetricsOutputPort
import br.com.rodrigogurgel.ratelimiterexample.framework.adapter.output.datastore.redis.RateLimiterDatastoreOutputPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RateLimitInputPortConfig {

    @Bean("rateLimitAccountHandler")
    fun rateLimitAccountHandler(
        rateLimiterDatastoreOutputPort: RateLimiterDatastoreOutputPort,
        @Qualifier("rateLimitProductHandler")
        rateLimitProductHandler: Handler<RateLimitContext, RateLimitContext>
    ): RateLimitAccountHandler {
        val rateLimitAccountHandler = RateLimitAccountHandler(rateLimitDatastoreOutputPort = rateLimiterDatastoreOutputPort)
        rateLimitAccountHandler.setNext(rateLimitProductHandler)
        return rateLimitAccountHandler
    }

    @Bean("rateLimitProductHandler")
    fun rateLimitProductHandler(rateLimiterDatastoreOutputPort: RateLimiterDatastoreOutputPort): RateLimitProductHandler {
        return RateLimitProductHandler(rateLimitDatastoreOutputPort = rateLimiterDatastoreOutputPort)
    }

    @Bean
    fun rateLimitValidator(
        @Qualifier("rateLimitAccountHandler")
        rateLimitProductHandler: Handler<RateLimitContext, RateLimitContext>,
        rateLimitMetricsOutputPort: RateLimitMetricsOutputPort
    ): RateLimitValidator {
        return RateLimitValidator(
            primaryHandler = rateLimitProductHandler,
            rateLimitMetricsOutputPort = rateLimitMetricsOutputPort
        )
    }
}