package br.com.rodrigogurgel.ratelimiterexample

import br.com.rodrigogurgel.ratelimiterexample.framework.config.redis.properties.RateLimitProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimiterExampleApplication

fun main(args: Array<String>) {
	runApplication<RateLimiterExampleApplication>(*args)
}
