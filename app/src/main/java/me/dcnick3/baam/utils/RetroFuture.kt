package me.dcnick3.baam.utils

import java9.util.concurrent.CompletableFuture
import java9.util.concurrent.CompletionException
import java9.util.concurrent.CompletionStage
import java9.util.function.BiFunction
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits for completion of [CompletionStage] without blocking a thread.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting, this function
 * stops waiting for the completion stage and immediately resumes with [CancellationException][kotlinx.coroutines.CancellationException].
 *
 * This method is intended to be used with one-shot futures, so on coroutine cancellation the [CompletableFuture] that
 * corresponds to this [CompletionStage] (see [CompletionStage.toCompletableFuture])
 * is cancelled. If cancelling the given stage is undesired, `stage.asDeferred().await()` should be used instead.
 */
suspend fun <T> CompletionStage<T>.await(): T {
    val future = toCompletableFuture() // retrieve the future
    // fast path when CompletableFuture is already done (does not suspend)
    if (future.isDone) {
        try {
            return future.get() as T
        } catch (e: ExecutionException) {
            throw e.cause ?: e // unwrap original cause from ExecutionException
        }
    }
    // slow path -- suspend
    return suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
        val consumer = ContinuationHandler(cont)
        handle(consumer)
        cont.invokeOnCancellation {
            future.cancel(false)
            consumer.cont = null // shall clear reference to continuation to aid GC
        }
    }
}

private class ContinuationHandler<T>(
    @Volatile @JvmField var cont: Continuation<T>?
) : BiFunction<T?, Throwable?, Unit> {
    @Suppress("UNCHECKED_CAST")
    override fun apply(result: T?, exception: Throwable?) {
        val cont = this.cont ?: return // atomically read current value unless null
        if (exception == null) {
            // the future has completed normally
            cont.resume(result as T)
        } else {
            // the future has completed with an exception, unwrap it to provide consistent view of .await() result and to propagate only original exception
            cont.resumeWithException((exception as? CompletionException)?.cause ?: exception)
        }
    }
}