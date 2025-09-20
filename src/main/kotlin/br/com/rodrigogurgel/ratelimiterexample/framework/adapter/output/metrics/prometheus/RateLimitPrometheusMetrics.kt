package br.com.rodrigogurgel.ratelimiterexample.framework.adapter.output.metrics.prometheus

import br.com.rodrigogurgel.ratelimiterexample.application.output.metrics.RateLimitMetrics
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RateLimitPrometheusMetrics(private val registry: PrometheusMeterRegistry) : RateLimitMetrics {
    override fun recordAccountHit(account: String, allowed: Boolean) {
        val counter = registry.counter(
            "app_rate_limit_account_requests_total",
            Tags.of("account", account, "allowed", if (allowed) "true" else "false")
        )
        counter.increment()
    }

    override fun recordProductHit(product: String, allowed: Boolean) {
        val counter = registry.counter(
            "app_rate_limit_product_requests_total",
            Tags.of("product", product, "allowed", if (allowed) "true" else "false")
        )
        counter.increment()
    }

    override fun recordLatency(millis: Long) {
        val timer = registry.timer("app_rate_limit_latency_ms")
        timer.record(Duration.ofMillis(millis))
    }
}