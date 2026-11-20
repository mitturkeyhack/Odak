package com.erkamyazilim.odak

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Tasarımdaki renkler ve küçük yardımcılar. */
object C {
    const val BG = 0xFFF6F5F1.toInt()
    const val INK = 0xFF16181D.toInt()
    const val MUTED = 0xFF4A4E57.toInt()
    const val SUB = 0xFF5B5F68.toInt()
    const val TEAL = 0xFF0F766E.toInt()
    const val TEAL_SOFT = 0xFFE7F3F1.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val LINE = 0xFFE6E4DD.toInt()
    const val DIVIDER = 0xFFEEECE6.toInt()
    const val NOTE = 0xFFEFEDE6.toInt()
    const val OFF_CARD = 0xFFE3E1DA.toInt()
    const val TRACK_OFF = 0xFFC4C7CC.toInt()
    const val DARK = 0xFF12201E.toInt()
    const val DARK_TEXT = 0xFFC9D6D3.toInt()
    const val DARK_SUB = 0xFFA9BAB6.toInt()
    const val MINT = 0xFF7FD6CB.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.rounded(color: Int, radiusDp: Float, strokeDp: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpf(radiusDp)
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

fun Context.label(s: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

fun Context.icon(res: Int, color: Int, sizeDp: Int): ImageView =
    ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

fun Context.iconBox(res: Int, iconColor: Int, bg: Int, boxDp: Int, iconDp: Int, radiusDp: Float): LinearLayout =
    LinearLayout(this).apply {
        gravity = Gravity.CENTER
        background = rounded(bg, radiusDp)
        addView(icon(res, iconColor, iconDp))
        layoutParams = LinearLayout.LayoutParams(dp(boxDp), dp(boxDp))
    }

fun Context.vertical(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.horizontal(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

/** Çocuğu üst boşlukla ekler. */
fun LinearLayout.add(
    v: View,
    topDp: Int = 0,
    width: Int = LinearLayout.LayoutParams.MATCH_PARENT,
    height: Int = LinearLayout.LayoutParams.WRAP_CONTENT,
    weight: Float = 0f
): View {
    val lp = LinearLayout.LayoutParams(width, height, weight)
    lp.topMargin = context.dp(topDp)
    addView(v, lp)
    return v
}

/** Tasarımdaki yuvarlak açma/kapama anahtarı. */
class ToggleView(context: Context) : View(context) {
    var on: Boolean = false
        set(v) { field = v; invalidate() }
    var dimmed: Boolean = false
        set(v) { field = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(60), context.dp(44))
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val tw = 52 * d
        val th = 32 * d
        val left = width - tw
        val top = (height - th) / 2f
        paint.color = if (on) C.TEAL else C.TRACK_OFF
        paint.alpha = if (dimmed) 150 else 255
        canvas.drawRoundRect(left, top, left + tw, top + th, th / 2f, th / 2f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.alpha = 255
        val r = 13 * d
        val cx = if (on) left + tw - 3 * d - r else left + 3 * d + r
        canvas.drawCircle(cx, top + th / 2f, r, paint)
    }
}
