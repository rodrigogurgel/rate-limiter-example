package br.com.rodrigogurgel.ratelimiterexample.framework.adapter.output.datastore.redis

import br.com.rodrigogurgel.ratelimiterexample.application.output.metrics.RateLimitMetricsDatastoreOutputPort
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.RedisDatastoreOutputPort
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.response.RateLimitResponse
import br.com.rodrigogurgel.ratelimiterexample.common.logger.extensions.rateLimiterFailure
import br.com.rodrigogurgel.ratelimiterexample.common.logger.extensions.rateLimiterSuccessfully
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import kotlin.time.measureTimedValue

@Service
class RateLimiterPrometheusDatastoreOutputPort(
    private val redisTemplate: RedisTemplate<String, String>,
    private val rateLimiterScript: DefaultRedisScript<List<Long>>,
    private val rateLimitMetricsDatastoreOutputPort: RateLimitMetricsDatastoreOutputPort
) : RedisDatastoreOutputPort {

    companion object {
        private val logger = LoggerFactory.getLogger(RateLimiterPrometheusDatastoreOutputPort::class.java)
    }

    override fun tryConsume(request: RateLimitRequest): RateLimitResponse = runCatching {
        val (result, duration) = measureTimedValue {
            redisTemplate.execute(
                rateLimiterScript,
                request.keys,
                *request.asArgs()
            )
        }

        val response = RateLimitResponse(result)

        rateLimitMetricsDatastoreOutputPort.recordHit(
            account = request.account.key,
            product = request.product.key,
            allowed = response.allowed
        )

        rateLimitMetricsDatastoreOutputPort.recordLatency(millis = duration.inWholeMilliseconds)

        logger.rateLimiterSuccessfully(
            accountKey = request.account.key,
            productKey = request.product.key,
            allowed = response.allowed,
        )

        return response
    }.onFailure {
        logger.rateLimiterFailure(
            accountKey = request.account.key,
            productKey = request.product.key,
            throwable = it
        )
    }.getOrElse {
        return if (request.allowOnError) RateLimitResponse.defaultAllowed(request)
        else RateLimitResponse.defaultDisallowed(request)
    }
}