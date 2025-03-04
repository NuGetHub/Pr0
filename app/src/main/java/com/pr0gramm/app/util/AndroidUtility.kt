package com.pr0gramm.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.util.LruCache
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.TaskStackBuilder
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.use
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.ConnectivityManagerCompat
import androidx.core.text.inSpans
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.DebugConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.PermissionHelper
import com.pr0gramm.app.ui.base.AsyncScope
import io.sentry.Sentry
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import io.sentry.event.interfaces.ExceptionInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rx.exceptions.OnErrorNotImplementedException
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Place to put everything that belongs nowhere. Thanks Obama.
 */
object AndroidUtility {
    private val logger = Logger("AndroidUtility")

    private val EXCEPTION_BLACKLIST = listOf("MediaCodec", "dequeueInputBuffer", "dequeueOutputBuffer", "releaseOutputBuffer", "native_")

    private val cache = LruCache<Int, Unit>(6)

    /**
     * Gets the height of the action bar as definied in the style attribute
     * [R.attr.actionBarSize] plus the height of the status bar on android
     * Kitkat and above.

     * @param context A context to resolve the styled attribute value for
     */
    fun getActionBarContentOffset(context: Context): Int {
        return getStatusBarHeight(context) + getActionBarHeight(context)
    }

    /**
     * Gets the height of the actionbar.
     */
    fun getActionBarHeight(context: Context): Int {
        context.obtainStyledAttributes(intArrayOf(R.attr.actionBarSize)).use {
            return it.getDimensionPixelSize(it.getIndex(0), -1)
        }
    }

    /**
     * Gets the height of the statusbar.
     */
    fun getStatusBarHeight(context: Context): Int {
        var result = 0

        val resources = context.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }

        return result
    }

    fun logToCrashlytics(error_: Throwable?) {
        if (error_ == null)
            return

        var error = error_
        val causalChain = error.causalChain

        if (causalChain.containsType<CancellationException>()) {
            return
        }

        if (causalChain.containsType<PermissionHelper.PermissionNotGranted>()) {
            return
        }

        if (causalChain.containsType<IOException>() || causalChain.containsType<HttpException>()) {
            logger.warn(error) { "Ignoring network exception" }
            return
        }

        if (causalChain.containsType<OnErrorNotImplementedException>()) {
            error = error.rootCause
        }

        try {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            if (EXCEPTION_BLACKLIST.any { it in trace }) {
                logger.warn("Ignoring exception", error)
                return
            }

            val errorStr = error.toString()
            if ("connect timed out" in errorStr)
                return

            // try to rate limit exceptions.
            val key = System.identityHashCode(error)
            if (cache.get(key) != null) {
                return
            } else {
                cache.put(key, Unit)
            }

            // log to sentry if a client is configured
            Sentry.getStoredClient()?.sendEvent(EventBuilder()
                    .withMessage(error.message)
                    .withLevel(Event.Level.WARNING)
                    .withSentryInterface(ExceptionInterface(error)))

        } catch (err: Throwable) {
            logger.warn(err) { "Could not send error $error to sentry" }
        }
    }

    fun isOnMobile(context: Context?): Boolean {
        context ?: return false

        val cm = context.getSystemService(
                Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return ConnectivityManagerCompat.isActiveNetworkMetered(cm)
    }

    /**
     * Gets the color tinted hq-icon
     */
    fun getTintedDrawable(context: Context, @DrawableRes drawableId: Int, @ColorRes colorId: Int): Drawable {
        val resources = context.resources
        val icon = DrawableCompat.wrap(AppCompatResources.getDrawable(context, drawableId)!!)
        DrawableCompat.setTint(icon, ResourcesCompat.getColor(resources, colorId, null))
        return icon
    }

    /**
     * Returns a CharSequence containing a bulleted and properly indented list.

     * @param leadingMargin In pixels, the space between the left edge of the bullet and the left edge of the text.
     * *
     * @param lines         An array of CharSequences. Each CharSequences will be a separate line/bullet-point.
     */
    fun makeBulletList(leadingMargin: Int, lines: List<CharSequence>): CharSequence {
        return SpannableStringBuilder().apply {
            for (idx in lines.indices) {
                inSpans(BulletSpan(leadingMargin / 3)) {
                    inSpans(LeadingMarginSpan.Standard(leadingMargin)) {
                        append(lines[idx])
                    }
                }

                val last = idx == lines.lastIndex
                append(if (last) "" else "\n")
            }
        }
    }

    fun buildVersionCode(): Int {
        if (BuildConfig.DEBUG) {
            return DebugConfig.versionOverride ?: BuildConfig.VERSION_CODE
        } else {
            return BuildConfig.VERSION_CODE
        }
    }

    fun hideSoftKeyboard(view: View?) {
        if (view != null) {
            try {
                val imm = view.context
                        .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

                imm.hideSoftInputFromWindow(view.windowToken, 0)
            } catch (ignored: Exception) {
            }
        }
    }

    fun showSoftKeyboard(view: EditText?) {
        if (view != null) {
            try {
                view.requestFocus()

                val imm = view.context.getSystemService<InputMethodManager>() ?: return
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)

            } catch (ignored: Exception) {
            }
        }
    }

    fun recreateActivity(activity: Activity) {
        val intent = Intent(activity.intent)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        TaskStackBuilder.create(activity)
                .addNextIntentWithParentStack(intent)
                .startActivities()
    }

    fun applyWindowFullscreen(activity: Activity, fullscreen: Boolean) {
        var flags = 0

        if (fullscreen) {
            flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

            flags = flags or (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)

            flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        val decorView = activity.window.decorView
        decorView.systemUiVisibility = flags
    }

    fun screenSize(activity: Activity): Point {
        val screenSize = Point()
        val display = activity.windowManager.defaultDisplay

        display.getRealSize(screenSize)

        return screenSize
    }

    fun screenIsLandscape(activity: Activity?): Boolean {
        if (activity == null) {
            return false
        }

        val size = screenSize(activity)
        return size.x > size.y
    }

    /**
     * Tries to get a basic activity from the given context. Returns an empty observable,
     * if no activity could be found.
     */
    fun activityFromContext(context: Context): Activity? {
        if (context is Activity)
            return context

        if (context is ContextWrapper)
            return activityFromContext(context.baseContext)

        return null
    }

    @ColorInt
    fun resolveColorAttribute(context: Context, attr: Int): Int {
        val arr = context.obtainStyledAttributes(intArrayOf(attr))
        try {
            return arr.getColor(arr.getIndex(0), 0)
        } finally {
            arr.recycle()
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun checkMainThread() = debug {
    if (Looper.getMainLooper().thread !== Thread.currentThread()) {
        Logger("AndroidUtility").error { "Expected to be in main thread but was: ${Thread.currentThread().name}" }
        throw IllegalStateException("Must be called from the main thread.")
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun checkNotMainThread(msg: String? = null) = debug {
    if (Looper.getMainLooper().thread === Thread.currentThread()) {
        Logger("AndroidUtility").error { "Expected not to be on main thread: $msg" }
        throw IllegalStateException("Must not be called from the main thread: $msg")
    }
}

inline fun <T> doInBackground(crossinline action: suspend () -> T): Job {
    return AsyncScope.launch {
        try {
            action()
        } catch (thr: Throwable) {
            // log it
            AndroidUtility.logToCrashlytics(BackgroundThreadException(thr))
        }
    }
}

class BackgroundThreadException(cause: Throwable) : RuntimeException(cause)

fun Throwable.getMessageWithCauses(): String {
    val error = this
    val type = javaClass.name
            .replaceFirst(".+\\.".toRegex(), "")
            .replace('$', '.')

    val cause = error.cause

    val hasCause = cause != null && error !== cause
    val message = error.message ?: ""
    val hasMessage = message.isNotBlank() && (
            !hasCause || (cause != null && cause.javaClass.directName !in message))

    return if (hasMessage) {
        if (hasCause && cause != null) {
            "$type(${error.message}), caused by ${cause.getMessageWithCauses()}"
        } else {
            "$type(${error.message})"
        }
    } else {
        if (hasCause && cause != null) {
            "$type, caused by ${cause.getMessageWithCauses()}"
        } else {
            type
        }
    }
}