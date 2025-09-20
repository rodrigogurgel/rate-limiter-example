package br.com.rodrigogurgel.ratelimiterexample.framework.filter

import br.com.rodrigogurgel.ratelimiterexample.domain.vo.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.framework.adapter.output.datastore.redis.RateLimiterDatastoreOutputPort
import br.com.rodrigogurgel.ratelimiterexample.framework.config.redis.properties.RateLimitProperties
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import kotlin.math.ceil

@Component
@Order(5)
class ReactiveRateLimitingFilter(
    private val props: RateLimitProperties,
    private val rateLimiterDatastoreOutputPort: RateLimiterDatastoreOutputPort
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        if (props.excludePatterns.any { request.uri.path.startsWith(it) }) {
            return chain.filter(exchange)
        }

        val rateLimiterAccountKey = buildKey(accountKey(request), "account", request)
        val rateLimiterProductKey = buildKey(productKey(request), "product", request)

        return Mono.fromCallable {
            rateLimiterDatastoreOutputPort.tryConsume(
                RateLimitRequest(
                    allowOnError = props.allowOnError,
                    account = RateLimitRequest.Params(
                        enabled = props.account.enabled,
                        key = rateLimiterAccountKey,
                        capacity = props.account.limit.toInt(),
                        windowMs = props.account.windowMs.toInt(),
                    ),
                    product = RateLimitRequest.Params(
                        enabled = props.product.enabled,
                        key = rateLimiterProductKey,
                        capacity = props.product.limit.toInt(),
                        windowMs = props.product.windowMs.toInt(),
                    ),
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { res ->
                val h = exchange.response.headers
                h.add("X-RateLimit-Account-Key", rateLimiterAccountKey)
                h.add("X-RateLimit-Account-Limit", props.account.limit.toString())
                h.add("X-RateLimit-Account-Remaining", res.account.remaining.toString())
                h.add("X-RateLimit-Account-Reset", res.product.ttlMs.toString())

                h.add("X-RateLimit-Product-Key", rateLimiterProductKey)
                h.add("X-RateLimit-Product-Limit", props.product.limit.toString())
                h.add("X-RateLimit-Product-Remaining", res.product.remaining.toString())
                h.add("X-RateLimit-Product-Reset", res.product.ttlMs.toString())

                if (!res.allowed) {
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
        val pathBucket = req.uri.path.replace(Regex("\\d+"), ":id")
        return "rl:$type:$name:$method:$pathBucket"
    }
}

