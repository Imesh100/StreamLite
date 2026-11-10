package com.imesh.streamlite

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var input: EditText
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val items = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.urlInput)
        list = findViewById(R.id.history)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        list.adapter = adapter

        list.setOnItemClickListener { _, _, pos, _ -> play(items[pos]) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            Prefs.removeHistory(this, items[pos])
            refreshHistory()
            Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show()
            true
        }

        findViewById<Button>(R.id.playBtn).setOnClickListener { play(input.text.toString()) }
        findViewById<Button>(R.id.pasteBtn).setOnClickListener { paste() }
        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                play(input.text.toString())
                true
            } else {
                false
            }
        }

        handleSend(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshHistory()
    }

    private fun handleSend(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type == "text/plain") {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val m = Regex("""(?:https?|rtsp)://\S+""").find(text)
            input.setText(m?.value ?: text.trim())
        }
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
        } else {
            null
        }
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        } else {
            input.setText(text)
            input.setSelection(text.length)
        }
    }

    private fun play(raw: String) {
        var u = raw.trim()
        if (u.isEmpty()) {
            input.error = "Enter a link"
            return
        }
        if (!u.contains("://")) u = "https://$u"
        Prefs.addHistory(this, u)
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, u))
    }

    private fun refreshHistory() {
        items.clear()
        items.addAll(Prefs.history(this))
        adapter.notifyDataSetChanged()
    }
}
