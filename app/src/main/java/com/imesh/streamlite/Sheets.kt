package com.imesh.streamlite

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Small helpers for bottom sheets and dialogs, used by every screen. */
object Sheets {

    fun show(ctx: Context, title: String?, vararg views: View): BottomSheetDialog {
        val d = BottomSheetDialog(ctx)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Fmt.dp(ctx, 20), Fmt.dp(ctx, 8), Fmt.dp(ctx, 20), Fmt.dp(ctx, 24))
        if (title != null) {
            val t = TextView(ctx)
            t.text = title
            t.textSize = 18f
            t.typeface = Typeface.DEFAULT_BOLD
            t.setTextColor(Fmt.attr(ctx, com.google.android.material.R.attr.colorOnSurface))
            t.setPadding(0, Fmt.dp(ctx, 8), 0, Fmt.dp(ctx, 12))
            root.addView(t)
        }
        for (v in views) root.addView(v)
        val scroll = NestedScrollView(ctx)
        scroll.addView(root)
        d.setContentView(scroll)
        d.setOnShowListener {
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            d.behavior.skipCollapsed = true
        }
        d.show()
        return d
    }

    fun optionRows(ctx: Context, labels: List<String>, checked: Int, onPick: (Int) -> Unit): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val inf = LayoutInflater.from(ctx)
        labels.forEachIndexed { i, label ->
            val row = inf.inflate(R.layout.row_choice, box, false)
            row.findViewById<TextView>(R.id.rowText).text = label
            row.findViewById<View>(R.id.rowCheck).visibility = if (i == checked) View.VISIBLE else View.INVISIBLE
            row.setOnClickListener { onPick(i) }
            box.addView(row)
        }
        return box
    }

    fun choose(ctx: Context, title: String, labels: List<String>, checked: Int, onPick: (Int) -> Unit) {
        var dialog: BottomSheetDialog? = null
        dialog = show(ctx, title, optionRows(ctx, labels, checked) { i ->
            dialog?.dismiss()
            onPick(i)
        })
    }

    /** A "label  [-] value [+]" row. onStep gets -1 or +1 and returns the new value text. */
    fun stepper(ctx: Context, label: String, initial: String, onStep: (Int) -> String): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.row_stepper, null, false)
        val value = v.findViewById<TextView>(R.id.stepValue)
        v.findViewById<TextView>(R.id.stepLabel).text = label
        value.text = initial
        v.findViewById<View>(R.id.stepMinus).setOnClickListener { value.text = onStep(-1) }
        v.findViewById<View>(R.id.stepPlus).setOnClickListener { value.text = onStep(1) }
        return v
    }

    fun sliderRow(
        ctx: Context, label: String, from: Float, to: Float, step: Float, value: Float,
        format: (Float) -> String, onChange: (Float) -> Unit
    ): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.row_slider, null, false)
        val valueText = v.findViewById<TextView>(R.id.sliderValue)
        val slider = v.findViewById<Slider>(R.id.sliderBar)
        v.findViewById<TextView>(R.id.sliderLabel).text = label
        slider.valueFrom = from
        slider.valueTo = to
        slider.stepSize = step
        slider.value = value.coerceIn(from, to)
        valueText.text = format(slider.value)
        slider.addOnChangeListener { _, newValue, fromUser ->
            valueText.text = format(newValue)
            if (fromUser) onChange(newValue)
        }
        return v
    }

    fun input(
        ctx: Context, title: String, hint: String, initial: String, multiline: Boolean,
        onOk: (String) -> Unit
    ) {
        val til = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        til.hint = hint
        val et = TextInputEditText(til.context)
        et.setText(initial)
        if (multiline) {
            et.minLines = 3
            et.maxLines = 8
            et.isSingleLine = false
        } else {
            et.setSingleLine(true)
        }
        til.addView(et)
        val frame = FrameLayout(ctx)
        val pad = Fmt.dp(ctx, 20)
        frame.setPadding(pad, Fmt.dp(ctx, 8), pad, 0)
        frame.addView(til)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(frame)
            .setPositiveButton("OK") { _, _ -> onOk(et.text?.toString() ?: "") }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
