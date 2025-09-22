package br.com.rodrigogurgel.ratelimiterexample.common.logger.extensions

import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.Logger
import org.slf4j.Marker

private const val CONTENT = "content"

private const val ACCOUNT_KEY = "account_key"
private const val PRODUCT_KEY = "product_key"
private const val ALLOWED = "allowed"

private fun buildMarker(vararg values: Pair<String, Any?>): Marker {
    return appendEntries(mutableMapOf(*values))
}

fun Logger.success(action: String, vararg content: Pair<String, Any?>) {
    val marker = buildMarker(CONTENT to mapOf(*content))
    info(marker, "$action execution completed successfully.")
}

fun Logger.failure(action: String, throwable: Throwable, vararg content: Pair<String, Any?>) {
    val marker = buildMarker(CONTENT to mapOf(*content))
    error(marker, "$action execution completed with errors.", throwable)
}

fun Logger.rateLimiterSuccessfully(accountKey: String, productKey: String, allowed: Boolean) {
    success("Rate limiter", ACCOUNT_KEY to accountKey, PRODUCT_KEY to productKey, ALLOWED to allowed)
}

fun Logger.rateLimiterFailure(accountKey: String, productKey: String, throwable: Throwable) {
    failure("Rate limiter", throwable, ACCOUNT_KEY to accountKey, PRODUCT_KEY to productKey)
}