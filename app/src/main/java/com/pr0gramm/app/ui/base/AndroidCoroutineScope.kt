package com.pr0gramm.app.ui.base

import android.content.Context
import android.view.View
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.ui.fragments.withBusyDialog
import com.pr0gramm.app.util.*
import kotlinx.coroutines.*
import retrofit2.HttpException
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.properties.Delegates


interface AndroidCoroutineScope : CoroutineScope {
    var job: Job

    val androidContext: Context

    override val coroutineContext: CoroutineContext
        get() = job + Main + DefaultCoroutineExceptionHandler

    fun launchWithErrorHandler(
            context: CoroutineContext = EmptyCoroutineContext,
            start: CoroutineStart = CoroutineStart.DEFAULT,
            busyIndicator: Boolean = false,
            block: suspend CoroutineScope.() -> Unit
    ): Job {
        return launch(context, start) {
            try {
                if (busyIndicator) {
                    withBusyDialog { block() }
                } else {
                    block()
                }
            } catch (err: Throwable) {
                if (err !is CancellationException) {
                    ErrorDialogFragment.defaultOnError().call(err)
                }
            }
        }
    }

    fun newChild(): AndroidCoroutineScope = childAndroidScope(this)

    fun cancelScope() {
        job.cancel()
    }
}

private fun childAndroidScope(parent: AndroidCoroutineScope): AndroidCoroutineScope {
    return object : AndroidCoroutineScope {
        override var job: Job = SupervisorJob(parent.job)
        override val androidContext: Context = parent.androidContext
    }
}


fun CoroutineScope.launchIgnoreErrors(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit): Job {

    return launch(context, start) {
        catchAll { block() }
    }
}

inline fun <T> withViewDisabled(vararg views: View, block: () -> T): T {
    checkMainThread()

    views.forEach { it.isEnabled = false }
    try {
        return block()
    } finally {
        views.forEach { it.isEnabled = true }
    }
}

inline fun <T> withViewVisible(vararg views: View, block: () -> T): T {
    checkMainThread()

    views.forEach { it.visible = true }
    try {
        return block()
    } finally {
        views.forEach { it.visible = true }
    }
}

suspend inline fun <T> withBackgroundContext(
        context: CoroutineContext? = null,
        noinline block: suspend CoroutineScope.() -> T): T {

    val newContext = if (context != null) context + Async else Async
    return withContext(newContext, block)
}

private val DefaultCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    if (throwable is CancellationException) {
        return@CoroutineExceptionHandler
    }

    throwable.causalChain.let { causalChain ->
        if (causalChain.containsType<IOException>() || causalChain.containsType<HttpException>()) {
            Logger("Background").warn(throwable) {
                "Ignoring uncaught IOException in background coroutine"
            }

            return@CoroutineExceptionHandler
        }
    }

    if (BuildConfig.DEBUG) {
        ErrorDialogFragment.defaultOnError().call(throwable)
    } else {
        AndroidUtility.logToCrashlytics(throwable)
    }
}

val Async = Dispatchers.IO
val AsyncScope get() = CoroutineScope(Async) + DefaultCoroutineExceptionHandler

val Main = Dispatchers.Main

suspend fun <T : Any?> Observable<T>.await(): T {
    val def = CompletableDeferred<T>()

    val sub = single().subscribe({ def.complete(it) }, { def.completeExceptionally(it) })

    def.invokeOnCompletion {
        if (def.isCancelled) {
            sub.unsubscribe()
        }
    }

    return def.await()
}

fun <T> toObservable(scheduler: Scheduler = Schedulers.computation(), block: suspend () -> T): Observable<T> {
    val observable = createObservable<T> { emitter ->
        val job = AsyncScope.launch {
            try {
                emitter.onNext(block())
                emitter.onCompleted()
            } catch (err: CancellationException) {
                // ignored
            } catch (err: Throwable) {
                emitter.onError(err)
            }
        }

        emitter.setCancellation {
            job.cancel()
        }
    }

    return observable.observeOn(scheduler)
}

inline fun <T> retryUpTo(tryCount: Int, delay: () -> Unit = {}, block: () -> T): T {
    var error: Throwable by Delegates.notNull()

    repeat(tryCount) {
        try {
            return@retryUpTo block()
        } catch (err: Throwable) {
            error = err
            delay()
        }
    }

    throw error
}
