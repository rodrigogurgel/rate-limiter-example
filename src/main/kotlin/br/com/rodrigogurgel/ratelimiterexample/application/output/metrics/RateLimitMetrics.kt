package br.com.rodrigogurgel.ratelimiterexample.application.output.metrics

interface RateLimitMetrics {
    fun recordAccountHit(account: String, allowed: Boolean)
    fun recordProductHit(product: String, allowed: Boolean)
    fun recordLatency(millis: Long)
}