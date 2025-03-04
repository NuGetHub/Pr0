package com.pr0gramm.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.FrameLayout
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.Themes
import com.pr0gramm.app.ui.paint
import com.pr0gramm.app.util.dip2px
import com.pr0gramm.app.util.getColorCompat
import com.pr0gramm.app.util.memorize
import com.pr0gramm.app.util.observeChangeEx
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 */
class CommentSpacerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val basePaddingLeft = paddingLeft

    private val lineWidth = context.dip2px(1f)
    private val lineMargin = context.dip2px(8f)

    private val logger = Logger("CommentSpacerView")

    var depth: Int by observeChangeEx(-1) { oldValue, newValue ->
        if (oldValue != newValue) {
            val paddingLeft = spaceAtDepth(newValue).toInt()
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
            requestLayout()
        }
    }

    var spacings: Long by observeChangeEx(0L) { oldValue, newValue ->
        if (oldValue != newValue) {
            invalidate()
        }
    }

    init {
        // we need to overwrite the default of the view group.
        setWillNotDraw(false)

        if (isInEditMode) {
            depth = 5
            spacings = 5L
        }
    }

    private fun spaceAtDepth(depth: Int): Float {
        return basePaddingLeft + lineMargin * depth.toDouble().pow(1 / 1.2).toFloat()
    }

    override fun onDraw(canvas: Canvas) = logger.time("Draw spacer at depth $depth") {
        super.onDraw(canvas)

        val colorful = Settings.get().colorfulCommentLines

        val height = height.toFloat()

        val paint = paint {
            isAntiAlias = false
            style = Paint.Style.STROKE
            color = initialColor(context)
            strokeWidth = context.dip2px(1f)
        }

        val spacings = spacings
        for (idx in 1 until depth) {
            val bit = 1L shl (idx + 1)

            if (spacings and bit == 0L) {
                continue
            }

            val x = (spaceAtDepth(idx) - lineWidth).roundToInt().toFloat()

            if (colorful) {
                paint.color = colorValue(context, idx)
            }

            canvas.drawLine(x, 0f, x, height, paint)

        }

    }

    private fun dashPathEffect(height: Float): DashPathEffect {
        // calculate how many full repetitions of the given size we need.
        val size = context.dip2px(5f)
        val repetitions = (height / (2 * size)).roundToInt()
        val modifiedSize = 0.5f * height / repetitions

        return DashPathEffect(floatArrayOf(modifiedSize, modifiedSize), 0f)
    }

    companion object {
        private val initialColor by memorize<Context, Int> { context ->
            context.getColorCompat(R.color.comment_line)
        }

        private val cachedColorValues by memorize<Context, IntArray> { context ->
            // themes we want to use
            val themes = listOf(
                    Themes.ORANGE, Themes.BLUE, Themes.OLIVE, Themes.PINK, Themes.GREEN,
                    Themes.ORANGE, Themes.BLUE, Themes.OLIVE, Themes.PINK, Themes.GREEN)

            // start at our currently configured theme (if it is in the list of themes)
            val themeSelection = if (ThemeHelper.theme in themes) {
                themes.dropWhile { it !== ThemeHelper.theme }
            } else {
                themes
            }

            // get a list of the accent colors
            val colors = listOf(initialColor(context)) + themeSelection.take(5).map { theme ->
                blendColors(0.3f, initialColor(context), context.getColorCompat(theme.accentColor))
            }

            colors.toIntArray()
        }

        private fun colorValue(context: Context, depth: Int): Int {
            if (depth < 3) {
                return initialColor(context)
            }

            val colorValues = cachedColorValues(context)
            return colorValues[(depth - 3) % colorValues.size]
        }
    }
}

private fun blendColors(factor: Float, source: Int, target: Int): Int {
    val f = factor.coerceIn(0f, 1f)

    val sa = Color.alpha(source)
    val sr = Color.red(source)
    val sg = Color.green(source)
    val sb = Color.blue(source)

    val ta = Color.alpha(target)
    val tr = Color.red(target)
    val tg = Color.green(target)
    val tb = Color.blue(target)

    val a = (sa + f * (ta - sa)).roundToInt() and 0xff
    val r = (sr + f * (tr - sr)).roundToInt() and 0xff
    val g = (sg + f * (tg - sg)).roundToInt() and 0xff
    val b = (sb + f * (tb - sb)).roundToInt() and 0xff

    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
