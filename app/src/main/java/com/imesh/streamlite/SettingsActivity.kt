package com.imesh.streamlite

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val seek = findViewById<SeekBar>(R.id.bufSeek)
        val value = findViewById<TextView>(R.id.bufValue)

        // progress 0..31  ->  8..256 MB in steps of 8
        seek.max = 31
        val current = Prefs.bufferMb(this)
        seek.progress = (current / 8 - 1).coerceIn(0, 31)
        value.text = "$current MB"

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val mb = (progress + 1) * 8
                value.text = "$mb MB"
                if (fromUser) Prefs.setBufferMb(this@SettingsActivity, mb)
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        val spinner = findViewById<Spinner>(R.id.resSpinner)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, Prefs.RES_LABELS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(Prefs.maxRes(this).coerceIn(0, Prefs.RES_LABELS.size - 1))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Prefs.setMaxRes(this@SettingsActivity, position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.clearHistory).setOnClickListener {
            Prefs.clearHistory(this)
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
        }
    }
}
