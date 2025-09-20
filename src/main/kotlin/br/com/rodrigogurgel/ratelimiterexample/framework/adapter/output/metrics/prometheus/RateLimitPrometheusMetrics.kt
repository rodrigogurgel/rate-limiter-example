package br.com.rodrigogurgel.ratelimiterexample.framework.adapter.output.metrics.prometheus

import br.com.rodrigogurgel.ratelimiterexample.application.output.metrics.RateLimitMetrics
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RateLimitPrometheusMetrics(private val registry: PrometheusMeterRegistry) : RateLimitMetrics {
    override fun recordHit(account: String, product: String, allowed: Boolean) {
        val counter = registry.counter(
            "app_rate_limit_requests_total",
            Tags.of("account", account, "product", product, "allowed", if (allowed) "true" else "false")
        )
        counter.increment()
    }

    override fun recordLatency(account: String, product: String, millis: Long) {
        val timer = registry.timer("app_rate_limit_latency_ms", Tags.of("account", account, "product", product))
        timer.record(Duration.ofMillis(millis))
    }
}