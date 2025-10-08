package br.com.rodrigogurgel.ratelimiterexample.application.input.chain

abstract class Handler<C, R> {
    private var next: Handler<C, R>? = null

    fun setNext(handler: Handler<C, R>): Handler<C, R> {
        next = handler
        return handler
    }

    sealed class Step<C, R> {
        data class Next<C, R>(val ctx: C, val undo: (() -> Unit)? = null) : Step<C, R>()
        data class Stop<C, R>(val result: R) : Step<C, R>()
    }

    protected abstract fun process(ctx: C, mapper: (C) -> R): Step<C, R>

    fun handle(ctx: C, mapper: (C) -> R): R {
        val undoCommands = ArrayDeque<() -> Unit>()
        return handleInternal(ctx, mapper, undoCommands)
    }

    private fun handleInternal(ctx: C, mapper: (C) -> R, undoCommands: ArrayDeque<() -> Unit>): R {
        return when (val step = process(ctx, mapper)) {
            is Step.Stop -> {
                while (undoCommands.isNotEmpty()) {
                    runCatching { undoCommands.removeLast().invoke() }
                }
                step.result
            }

            is Step.Next -> {
                step.undo?.let { undoCommands.addLast(it) }

                val nextHandler = next
                if (nextHandler != null) {
                    nextHandler.handleInternal(step.ctx, mapper, undoCommands)
                } else {
                    val result = runCatching { mapper(step.ctx) }
                    if (result.isFailure) {
                        while (undoCommands.isNotEmpty()) {
                            runCatching { undoCommands.removeLast().invoke() }
                        }
                        throw result.exceptionOrNull()!!
                    }
                    result.getOrThrow()
                }
            }
        }
    }
}
