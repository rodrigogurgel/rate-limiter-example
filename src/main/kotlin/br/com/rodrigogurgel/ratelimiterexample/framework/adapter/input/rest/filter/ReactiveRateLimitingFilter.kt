package br.com.rodrigogurgel.ratelimiterexample.framework.adapter.input.rest.filter

import br.com.rodrigogurgel.ratelimiterexample.application.output.input.ratelimit.RateLimitContext
import br.com.rodrigogurgel.ratelimiterexample.application.output.input.ratelimit.RateLimitContext.RateLimitRequests
import br.com.rodrigogurgel.ratelimiterexample.application.output.input.ratelimit.RateLimitValidator
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.framework.config.redis.properties.RateLimitProperties
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Component
@Order(5)
class ReactiveRateLimitingFilter(
    private val props: RateLimitProperties,
    private val rateLimitValidator: RateLimitValidator
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        if (props.excludePatterns.any { request.uri.path.startsWith(it) }) {
            return chain.filter(exchange)
        }

        val rateLimiterAccountKey = buildKey(accountKey(request), "account", request)
        val rateLimiterProductKey = buildKey(productKey(request), "product", request)

        return Mono.fromCallable {
            val rateLimitAccountRequest = RateLimitRequest(
                allowOnError = props.account.allowOnError,
                enabled = props.account.enabled,
                key = rateLimiterAccountKey,
                capacity = props.account.limit.toInt(),
                windowMs = props.account.windowMs.toInt(),
            )

            val rateLimitProductRequest = RateLimitRequest(
                props.product.allowOnError,
                enabled = props.product.enabled,
                key = rateLimiterProductKey,
                capacity = props.product.limit.toInt(),
                windowMs = props.product.windowMs.toInt(),
            )

            val rateLimitContext = RateLimitContext(
                requests = RateLimitRequests(
                    account = rateLimitAccountRequest,
                    product = rateLimitProductRequest,
                ),
            )
            rateLimitValidator.validate(rateLimitContext)
        }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { rateLimitContext ->
                val h = exchange.response.headers
                val rateLimitAccountResponse = rateLimitContext.responses.account
                val rateLimitProductResponse = rateLimitContext.responses.product

                h.add("X-RateLimit-Account-Key", rateLimiterAccountKey)
                h.add("X-RateLimit-Account-Limit", props.account.limit.toString())

                rateLimitAccountResponse?.let {
                    h.add("X-RateLimit-Account-Remaining", rateLimitAccountResponse.remaining.toString())
                    h.add("X-RateLimit-Account-Reset", rateLimitAccountResponse.retryAfterMs.toString())
                }


                h.add("X-RateLimit-Product-Key", rateLimiterProductKey)
                h.add("X-RateLimit-Product-Limit", props.product.limit.toString())

                rateLimitProductResponse?.let {
                    h.add("X-RateLimit-Product-Remaining", rateLimitProductResponse.remaining.toString())
                    h.add("X-RateLimit-Product-Reset", rateLimitProductResponse.retryAfterMs.toString())
                }

                if (!rateLimitContext.responses.isAllowed()) {
                    exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                    return@flatMap exchange.response.setComplete()
                }
                chain.filter(exchange)
            }
    }

    fun accountKey(request: ServerHttpRequest): String {
        return request.headers.getFirst("X-RateLimit-Account") ?: "anon"
    }

    fun productKey(request: ServerHttpRequest): String {
        return request.headers.getFirst("X-RateLimit-Product") ?: "anon"
    }

    private fun buildKey(name: String, type: String, req: ServerHttpRequest): String {
        val method = req.method
        return "rl:$type:$name"
    }
}

