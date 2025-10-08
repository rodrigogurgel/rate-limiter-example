package br.com.rodrigogurgel.ratelimiterexample.framework.adapter.output.datastore.redis

import br.com.rodrigogurgel.ratelimiterexample.application.output.metrics.RateLimitMetricsOutputPort
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.RateLimitDatastoreOutputPort
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request.RateLimitRequest
import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.response.RateLimitResponse
import br.com.rodrigogurgel.ratelimiterexample.common.logger.extensions.compensationFailure
import br.com.rodrigogurgel.ratelimiterexample.common.logger.extensions.compensationSuccessfully
import br.com.rodrigogurgel.ratelimiterexample.common.logger.extensions.rateLimiterFailure
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import kotlin.time.measureTimedValue

@Service
class RateLimiterDatastoreOutputPort(
    private val redisTemplate: RedisTemplate<String, String>,
    private val rateLimiterScript: DefaultRedisScript<List<Long>>,
    private val rateLimitMetricsOutputPort: RateLimitMetricsOutputPort
) : RateLimitDatastoreOutputPort {

    companion object {
        private val logger = LoggerFactory.getLogger(RateLimiterDatastoreOutputPort::class.java)
    }

    override fun acquirePermit(request: RateLimitRequest): RateLimitResponse = runCatching {
        val (result, duration) = measureTimedValue {
            redisTemplate.execute(
                rateLimiterScript,
                listOf(request.key),
                *request.asArgs()
            )
        }

        val response = RateLimitResponse(result)

        rateLimitMetricsOutputPort.recordLatency(millis = duration.inWholeMilliseconds)

        return response
    }.onFailure {
        logger.rateLimiterFailure(
            key = request.key,
            throwable = it
        )
    }.getOrElse {
        return if (request.allowOnError) RateLimitResponse.defaultAllowed(request)
        else RateLimitResponse.defaultDenied(request)
    }

    override fun compensate(token: RateLimitResponse.CompensationToken) {
        runCatching {
            val deltaNeg = -token.delta.toLong()
            redisTemplate.opsForHash<String, Any>().increment(token.hashKey, token.bucketField, deltaNeg)
            rateLimitMetricsOutputPort.recordCompensation(token.hashKey)
            logger.compensationSuccessfully(
                token.hashKey,
            )
        }.onFailure {
            logger.compensationFailure(
                token.hashKey,
                it
            )
        }
    }
}