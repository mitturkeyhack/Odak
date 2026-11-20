package com.erkamyazilim.odak

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View

private fun Context.sp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

/** 7 günlük süre çubukları (değerler dakika). */
class BarChartView(
    context: Context,
    private val values: List<Int>,
    private val labels: List<String>,
    private val highlight: Int
) : View(context) {

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.sp(11f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(170))
    }

    override fun onDraw(canvas: Canvas) {
        val n = values.size
        if (n == 0) return
        val slot = width.toFloat() / n
        val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val top = context.dpf(20f)
        val bottom = height - context.dpf(24f)
        val area = bottom - top
        val bw = minOf(context.dpf(26f), slot * 0.6f)
        val r = context.dpf(5f)
        for (i in 0 until n) {
            val cx = slot * i + slot / 2f
            val v = values[i]
            val h = if (v == 0) context.dpf(3f) else maxOf(context.dpf(3f), area * v / maxV)
            val on = i == highlight
            bar.color = if (on) C.TEAL else 0xFFB9DDD7.toInt()
            canvas.drawRoundRect(cx - bw / 2f, bottom - h, cx + bw / 2f, bottom, r, r, bar)
            text.color = C.MUTED
            text.isFakeBoldText = on
            canvas.drawText(v.toString(), cx, bottom - h - context.dpf(5f), text)
            text.color = if (on) C.INK else C.SUB
            canvas.drawText(labels.getOrElse(i) { "" }, cx, height - context.dpf(6f), text)
        }
    }
}

/** Takipçi sayısı çizgisi. */
class LineChartView(context: Context, private val values: List<Int>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = C.TEAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(70))
    }

    override fun onDraw(canvas: Canvas) {
        if (values.size < 2) return
        val minV = values.minOrNull() ?: 0
        val maxV = values.maxOrNull() ?: 0
        val span = (maxV - minV).coerceAtLeast(1)
        val pad = context.dpf(6f)
        val w = width - 2 * pad
        val h = height - 2 * pad
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = pad + w * i / (values.size - 1)
            val y = pad + h - h * (v - minV) / span
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
}
