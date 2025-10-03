package br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.response

import br.com.rodrigogurgel.ratelimiterexample.application.output.ratelimit.request.RateLimitRequest

/**
 * Representa a resposta de execução do script Lua de rate limit
 * para um único escopo (ex.: account ou product).
 *
 * O script retorna 6 valores na seguinte ordem:
 *
 *  [0] allowed        -> 1 se permitido, 0 se negado
 *  [1] remaining      -> capacidade residual (pós-delta se permitido)
 *  [2] retryAfterMs   -> sugestão de espera até próximo sub-bucket
 *  [3] hashKey        -> chave do hash usada pelo escopo (para compensação)
 *  [4] bucketField    -> identificador do bucket (field no hash)
 *  [5] usedBefore     -> uso apurado antes do delta (telemetria/debug)
 */
class RateLimitResponse(result: List<Any>) {
    val allowed: Boolean
    val remaining: Int
    val retryAfterMs: Int
    val hashKey: String
    val bucketField: String
    val usedBefore: Int

    init {
        require(result.size == RESULT_SIZE) {
            "Result must have size $RESULT_SIZE, but got ${result.size}"
        }

        allowed = (result[ALLOWED_POSITION] as Number).toInt() == 1
        remaining = (result[REMAINING_POSITION] as Number).toInt()
        retryAfterMs = (result[RETRY_AFTER_POSITION] as Number).toInt()
        hashKey = result[HASH_KEY_POSITION] as String
        bucketField = result[BUCKET_FIELD_POSITION] as String
        usedBefore = (result[USED_BEFORE_POSITION] as Number).toInt()
    }

    companion object {
        const val RESULT_SIZE = 6
        private const val ALLOWED_POSITION = 0
        private const val REMAINING_POSITION = 1
        private const val RETRY_AFTER_POSITION = 2
        private const val HASH_KEY_POSITION = 3
        private const val BUCKET_FIELD_POSITION = 4
        private const val USED_BEFORE_POSITION = 5

        fun defaultAllowed(rateLimitRequest: RateLimitRequest): RateLimitResponse {
            val resultDefaultValues = listOf(
                1,                              // allowed
                rateLimitRequest.capacity,      // remaining
                rateLimitRequest.windowMs,      // retryAfter
                rateLimitRequest.key,           // hashKey
                "default-bucket",               // bucketField
                0                               // usedBefore
            )
            return RateLimitResponse(resultDefaultValues)
        }

        fun defaultDenied(rateLimitRequest: RateLimitRequest): RateLimitResponse {
            val resultDefaultValues = listOf(
                0,                              // allowed
                0,                              // remaining
                rateLimitRequest.windowMs,      // retryAfter
                rateLimitRequest.key,           // hashKey
                "default-bucket",               // bucketField
                rateLimitRequest.capacity       // usedBefore (simula cheio)
            )
            return RateLimitResponse(resultDefaultValues)
        }
    }

    /**
     * Cria um token de compensação para desfazer este consumo, se necessário.
     */
    fun toCompensationToken(delta: Int): CompensationToken? {
        return if (allowed && delta > 0) {
            CompensationToken(hashKey, bucketField, delta)
        } else null
    }

    data class CompensationToken(
        val hashKey: String,
        val bucketField: String,
        val delta: Int
    )
}
