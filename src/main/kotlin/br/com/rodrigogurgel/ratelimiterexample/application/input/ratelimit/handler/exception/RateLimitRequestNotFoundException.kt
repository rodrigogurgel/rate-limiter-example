package br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.handler.exception

import br.com.rodrigogurgel.ratelimiterexample.application.input.ratelimit.RateLimitType

data class RateLimitRequestNotFoundException(val type: RateLimitType): RuntimeException(
    "Request is null, type ${type.name} not found in RateLimitContext"
)