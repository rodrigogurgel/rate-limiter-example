package br.com.rodrigogurgel.ratelimiterexample.framework.adapter.output.metrics.prometheus

import br.com.rodrigogurgel.ratelimiterexample.application.output.metrics.RateLimitMetricsDatastoreOutputPort
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RateLimitPrometheusMetricsDatastore(private val registry: PrometheusMeterRegistry) : RateLimitMetricsDatastoreOutputPort {
    override fun recordHit(account: String, product: String, allowed: Boolean) {
        val counter = registry.counter(
            "app_rate_limit_requests_total",
            Tags.of("account", account, "product", product, "allowed", if (allowed) "true" else "false")
        )
        counter.increment()
    }


    override fun recordLatency(millis: Long) {
        val timer = registry.timer("app_rate_limit_latency_ms")
        timer.record(Duration.ofMillis(millis))
    }
}