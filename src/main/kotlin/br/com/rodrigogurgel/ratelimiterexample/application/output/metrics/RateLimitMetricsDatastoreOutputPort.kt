package br.com.rodrigogurgel.ratelimiterexample.application.output.metrics

interface RateLimitMetricsDatastoreOutputPort {
    fun recordHit(account: String, product: String, allowed: Boolean)
    fun recordLatency(millis: Long)
}