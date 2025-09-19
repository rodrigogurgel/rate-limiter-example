package br.com.rodrigogurgel.ratelimiterexample.domain.vo

data class RateLimitResult(
    val allowed: Boolean,
    val remaining: Long,
    val ttlMs: Long
)
