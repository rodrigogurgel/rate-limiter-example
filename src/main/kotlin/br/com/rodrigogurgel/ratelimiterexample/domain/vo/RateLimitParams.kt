package br.com.rodrigogurgel.ratelimiterexample.domain.vo

data class RateLimitRequest(
    val allowOnError: Boolean,
    val account: Params,
    val product: Params,
) {
    data class Params(
        val key: String,
        val enabled: Boolean,
        val capacity: Int,
        val windowMs: Int
    ) {
        fun asArgs() = arrayOf(
            enabledArg,
            capacity.toString(),
            windowMs.toString(),
        )

        private val enabledArg
            get() = "${if (enabled) 1 else 0}"
    }

    val keys = listOf(account.key, product.key)

    fun asArgs(): Array<String> {
        return account.asArgs() + product.asArgs()
    }
}
