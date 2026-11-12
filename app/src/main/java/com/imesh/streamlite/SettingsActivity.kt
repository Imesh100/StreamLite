package com.imesh.streamlite

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var subPreview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Theming.themeRes(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Theming.bars(this)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        subPreview = findViewById(R.id.subPreview)

        setupTheme()
        setupPlayback()
        setupSubtitles()
        setupUsage()
        setupTools()
    }

    private fun setupTheme() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.themeGroup)
        group.check(
            when (prefTheme) {
                1 -> R.id.themeDark
                2 -> R.id.themeAmoled
                else -> R.id.themeSystem
            }
        )
        group.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val mode = when (id) {
                R.id.themeDark -> 1
                R.id.themeAmoled -> 2
                else -> 0
            }
            if (mode == prefTheme) return@addOnButtonCheckedListener
            prefTheme = mode
            Theming.apply(this)
            recreate()
        }
    }

    private fun setupPlayback() {
        val bufValue = findViewById<TextView>(R.id.bufValue)
        val slider = findViewById<Slider>(R.id.bufSlider)
        slider.value = (prefBufferMb.coerceIn(8, 256) / 8 * 8).toFloat()
        bufValue.text = "${slider.value.toInt()} MB"
        slider.addOnChangeListener { _, v, fromUser ->
            bufValue.text = "${v.toInt()} MB"
            if (fromUser) prefBufferMb = v.toInt()
        }

        val resValue = findViewById<TextView>(R.id.resValue)
        resValue.text = RES_LABELS[prefMaxRes.coerceIn(0, RES_LABELS.size - 1)]
        findViewById<View>(R.id.resRow).setOnClickListener {
            Sheets.choose(this, "Maximum video quality", RES_LABELS.toList(), prefMaxRes) { i ->
                prefMaxRes = i
                resValue.text = RES_LABELS[i]
            }
        }

        bindSwitch(R.id.swResume, prefResume) { prefResume = it }
        bindSwitch(R.id.swReconnect, prefReconnect) { prefReconnect = it }
        bindSwitch(R.id.swPip, prefAutoPip) { prefAutoPip = it }
        bindSwitch(R.id.swFollow, prefFollowVideo) { prefFollowVideo = it }
        bindSwitch(R.id.swClip, prefAutoDetect) { prefAutoDetect = it }
        bindSwitch(R.id.swAutoSubs, prefAutoSubs) { prefAutoSubs = it }

        val decIds = intArrayOf(R.id.decAuto, R.id.decSoft, R.id.decHard)
        val decGroup = findViewById<MaterialButtonToggleGroup>(R.id.decoderGroup)
        decGroup.check(decIds[prefDecoderMode.coerceIn(0, 2)])
        decGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) prefDecoderMode = decIds.indexOf(id).coerceAtLeast(0)
        }
        bindSwitch(R.id.swTexture, prefTextureView) { prefTextureView = it }
    }

    private fun bindSwitch(id: Int, value: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<MaterialSwitch>(id)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun setupSubtitles() {
        val sizeValue = findViewById<TextView>(R.id.subSizeValue)
        val size = findViewById<Slider>(R.id.subSize)
        size.value = prefSubSize.coerceIn(50, 250).toFloat()
        sizeValue.text = "${size.value.toInt()}%"
        size.addOnChangeListener { _, v, fromUser ->
            sizeValue.text = "${v.toInt()}%"
            if (fromUser) {
                prefSubSize = v.toInt()
                updatePreview()
            }
        }

        val posValue = findViewById<TextView>(R.id.subPosValue)
        val pos = findViewById<Slider>(R.id.subPos)
        pos.value = prefSubPos.coerceIn(0, 30).toFloat()
        posValue.text = "${pos.value.toInt()}%"
        pos.addOnChangeListener { _, v, fromUser ->
            posValue.text = "${v.toInt()}%"
            if (fromUser) prefSubPos = v.toInt()
        }

        val colorIds = intArrayOf(R.id.subC0, R.id.subC1, R.id.subC2, R.id.subC3)
        colorIds.forEachIndexed { i, id -> findViewById<Button>(id).setTextColor(SUB_COLORS[i]) }
        val colorGroup = findViewById<MaterialButtonToggleGroup>(R.id.subColorGroup)
        colorGroup.check(colorIds[prefSubColor.coerceIn(0, 3)])
        colorGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                prefSubColor = colorIds.indexOf(id).coerceAtLeast(0)
                updatePreview()
            }
        }

        val styleIds = intArrayOf(R.id.subS0, R.id.subS1, R.id.subS2)
        val styleGroup = findViewById<MaterialButtonToggleGroup>(R.id.subStyleGroup)
        styleGroup.check(styleIds[prefSubStyle.coerceIn(0, 2)])
        styleGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                prefSubStyle = styleIds.indexOf(id).coerceAtLeast(0)
                updatePreview()
            }
        }

        val audio = findViewById<TextInputEditText>(R.id.audioLang)
        val text = findViewById<TextInputEditText>(R.id.textLang)
        audio.setText(prefAudioLang)
        text.setText(prefTextLang)
        audio.addTextChangedListener(watcher { prefAudioLang = it })
        text.addTextChangedListener(watcher { prefTextLang = it })

        updatePreview()
    }

    private fun watcher(onText: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = onText(s?.toString() ?: "")
    }

    private fun updatePreview() {
        subPreview.setTextColor(SUB_COLORS[prefSubColor.coerceIn(0, 3)])
        subPreview.textSize = 16f * prefSubSize / 100f
        subPreview.setBackgroundColor(Color.TRANSPARENT)
        subPreview.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        when (prefSubStyle) {
            0 -> subPreview.setShadowLayer(5f, 0f, 0f, Color.BLACK)
            1 -> subPreview.setShadowLayer(4f, 3f, 3f, Color.BLACK)
            else -> subPreview.setBackgroundColor(0xB3000000.toInt())
        }
    }

    private fun setupUsage() {
        val chart = findViewById<BarChartView>(R.id.usageChart)
        val (values, labels) = DataUsage.last7(this)
        chart.setData(values, labels)
        findViewById<TextView>(R.id.usageText).text =
            "Today: ${Fmt.bytes(DataUsage.today(this))}\n" +
                "This month: ${Fmt.bytes(DataUsage.thisMonth(this))}\n" +
                "All time: ${Fmt.bytes(DataUsage.total(this))}"
    }

    private fun setupTools() {
        findViewById<View>(R.id.btnDebug).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("StreamLite debug", DebugLog.report(this, "")))
            Toast.makeText(this, "Debug report copied", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnClear).setOnClickListener {
            Store.clearUnpinned(this)
            Toast.makeText(this, "Recent links cleared", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnResetUsage).setOnClickListener {
            DataUsage.reset(this)
            setupUsage()
            Toast.makeText(this, "Data usage reset", Toast.LENGTH_SHORT).show()
        }
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        findViewById<TextView>(R.id.versionText).text = "StreamLite $ver"
    }
}
