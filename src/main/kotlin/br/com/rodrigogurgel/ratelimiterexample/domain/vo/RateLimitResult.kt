package br.com.rodrigogurgel.ratelimiterexample.domain.vo

class RateLimitResult {
    val allowed: Boolean
    val account: Result
    val product: Result

    constructor(result: List<Number>) {
        require(result.size == RESULT_SIZE) { "Results must have size $RESULT_SIZE, but got ${result.size}" }

        allowed = result[ALLOWED_VALUE_POSITION].toInt() == 1
        account = Result(
            remaining = result[ACCOUNT_REMAINING_VALUE_POSITION].toInt(),
            ttlMs = result[ACCOUNT_TTL_MS_VALUE_POSITION].toInt()
        )
        product = Result(
            remaining = result[PRODUCT_REMAINING_VALUE_POSITION].toInt(),
            ttlMs = result[PRODUCT_TTL_MS_VALUE_POSITION].toInt()
        )
    }

    companion object {
        const val RESULT_SIZE = 5
        const val ALLOWED_VALUE_POSITION = 0
        const val ACCOUNT_REMAINING_VALUE_POSITION = 1
        const val ACCOUNT_TTL_MS_VALUE_POSITION = 2
        const val PRODUCT_REMAINING_VALUE_POSITION = 3
        const val PRODUCT_TTL_MS_VALUE_POSITION = 4

        fun defaultAllowed(rateLimitRequest: RateLimitRequest): RateLimitResult {
            val resultDefaultValues = with(rateLimitRequest) {
                listOf(
                    1,
                    account.capacity,
                    account.windowMs,
                    product.capacity,
                    product.windowMs,
                )
            }
            return RateLimitResult(resultDefaultValues)
        }

        fun defaultDisallowed(rateLimitRequest: RateLimitRequest): RateLimitResult {
            val resultDefaultValues = with(rateLimitRequest) {
                listOf(
                    0,
                    0,
                    account.windowMs,
                    0,
                    product.windowMs,
                )
            }
            return RateLimitResult(resultDefaultValues)
        }
    }

    data class Result(
        val remaining: Int,
        val ttlMs: Int
    )
}
