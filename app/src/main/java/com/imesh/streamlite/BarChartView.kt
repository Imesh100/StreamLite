package com.imesh.streamlite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Tiny 7-bar chart for the data usage card. */
class BarChartView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private var values: List<Long> = emptyList()
    private var labels: List<String> = emptyList()
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setData(v: List<Long>, l: List<String>) {
        values = v
        labels = l
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val n = values.size
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val slot = w / n
        val bw = slot * 0.5f
        val labelH = Fmt.dp(context, 20).toFloat()
        val topPad = Fmt.dp(context, 18).toFloat()
        val radius = Fmt.dp(context, 6).toFloat()
        val primary = Fmt.attr(context, androidx.appcompat.R.attr.colorPrimary)
        val track = Fmt.attr(context, R.attr.slOutline)
        val sub = Fmt.attr(context, R.attr.slText2)
        text.textAlign = Paint.Align.CENTER
        text.textSize = Fmt.dp(context, 11).toFloat()
        for (i in 0 until n) {
            val left = i * slot + (slot - bw) / 2f
            val avail = h - labelH - topPad
            val bh = (avail * values[i].toFloat() / max).coerceAtLeast(Fmt.dp(context, 4).toFloat())
            val top = h - labelH - bh
            bar.color = if (i == n - 1) primary else track
            rect.set(left, top, left + bw, h - labelH)
            canvas.drawRoundRect(rect, radius, radius, bar)
            text.color = sub
            canvas.drawText(labels[i], i * slot + slot / 2f, h - Fmt.dp(context, 4).toFloat(), text)
            val v = Fmt.bytesShort(values[i])
            if (v.isNotEmpty()) canvas.drawText(v, i * slot + slot / 2f, top - Fmt.dp(context, 4).toFloat(), text)
        }
    }
}
