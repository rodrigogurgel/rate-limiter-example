package br.com.rodrigogurgel.ratelimiterexample.application.output.metrics

interface RateLimitMetrics {
    fun recordHit(account: String, product: String, allowed: Boolean)
    fun recordLatency(account: String, product: String, millis: Long)
}