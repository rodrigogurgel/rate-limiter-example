package br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request

data class RateLimitRequest(
    val allowOnError: Boolean,
    val key: String,
    val enabled: Boolean,
    val capacity: Int,
    val windowMs: Int
) {
    private val enabledArg
        get() = "${if (enabled) 1 else 0}"

    fun asArgs() = arrayOf(
        enabledArg,
        capacity.toString(),
        windowMs.toString(),
    )
}