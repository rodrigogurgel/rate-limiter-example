package br.com.rodrigogurgel.ratelimiterexample.application.output.metrics

interface RateLimitMetricsOutputPort {
    fun recordHit(account: String, product: String, allowed: Boolean)
    fun recordLatency(millis: Long)
    fun recordCompensation(hashKey: String)
}