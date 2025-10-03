package br.com.rodrigogurgel.ratelimiterexample.common.logger.extensions

import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.Logger
import org.slf4j.Marker

private const val CONTENT = "content"

private const val KEY = "key"
private const val ACCOUNT_KEY = "account_key"
private const val PRODUCT_KEY = "product_key"
private const val ALLOWED = "allowed"

private fun buildMarker(vararg values: Pair<String, Any?>): Marker {
    return appendEntries(mutableMapOf(*values))
}

fun Logger.debugging(action: String, vararg content: Pair<String, Any?>) {
    val marker = buildMarker(CONTENT to mapOf(*content))
    debug(marker, "Debugging execution ${action.lowercase()}.")
}

fun Logger.success(action: String, vararg content: Pair<String, Any?>) {
    val marker = buildMarker(CONTENT to mapOf(*content))
    info(marker, "$action execution completed successfully.")
}

fun Logger.warning(action: String, throwable: Throwable, vararg content: Pair<String, Any?>) {
    val marker = buildMarker(CONTENT to mapOf(*content))
    error(marker, "$action execution completed with warning.", throwable)
}

fun Logger.failure(action: String, throwable: Throwable, vararg content: Pair<String, Any?>) {
    val marker = buildMarker(CONTENT to mapOf(*content))
    error(marker, "$action execution completed with errors.", throwable)
}

fun Logger.rateLimiterSuccessfully(account: String, product: String, allowed: Boolean) {
    debugging("Rate limiter", ACCOUNT_KEY to account, PRODUCT_KEY to product, ALLOWED to allowed)
}

fun Logger.rateLimiterFailure(key: String, throwable: Throwable) {
    failure("Rate limiter", throwable, KEY to key)
}

fun Logger.compensationSuccessfully(key: String) {
    success("Compensation", KEY to key)
}

fun Logger.compensationFailure(key: String, throwable: Throwable) {
    warning("Compensation", throwable, KEY to key)
}