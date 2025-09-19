package br.com.rodrigogurgel.ratelimiterexample.framework.filter

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

        val key = buildKey(request)

        return Mono.fromCallable {
            rateLimiterDatastoreOutputPort.tryConsume(key, props.limit, props.windowMs)
        }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { res ->
                val h = exchange.response.headers
                h.add("X-RateLimit-Key", key)
                h.add("X-RateLimit-Limit", props.limit.toString())
                h.add("X-RateLimit-Remaining", res.remaining.toString())
                h.add("X-RateLimit-Reset", res.ttlMs.toString())

                if (!res.allowed) {
                    exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                    h.add("Retry-After", ceil(res.ttlMs / 1000.0).toLong().toString())
                    return@flatMap exchange.response.setComplete()
                }
                chain.filter(exchange)
            }
    }

    private fun buildKey(req: ServerHttpRequest): String {
        val subject = req.headers.getFirst("X-Auth-User")
            ?: req.headers.getFirst("X-Forwarded-For")?.split(",")?.first()?.trim()
            ?: req.remoteAddress?.address?.hostAddress
            ?: "anon"
        val method = req.method
        val pathBucket = req.uri.path.replace(Regex("\\d+"), ":id")
        return "rl:$subject:$method:$pathBucket"
    }
}

