package com.imesh.streamlite

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    companion object {
        /** Last copied link we already offered, so the banner doesn't keep returning. */
        private var lastOffered = ""
        private val LINK_RE = Regex("""(?:https?|rtsp)://[^\s"'<>]+""")
    }

    private lateinit var input: TextInputEditText
    private lateinit var headersInput: TextInputEditText
    private lateinit var headersLayout: TextInputLayout
    private lateinit var advToggle: TextView
    private lateinit var clipCard: MaterialCardView
    private lateinit var clipText: TextView
    private lateinit var empty: View
    private lateinit var adapter: EntryAdapter
    private var clipUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Theming.themeRes(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Theming.bars(this)

        input = findViewById(R.id.urlInput)
        headersInput = findViewById(R.id.headersInput)
        headersLayout = findViewById(R.id.headersLayout)
        advToggle = findViewById(R.id.advToggle)
        clipCard = findViewById(R.id.clipCard)
        clipText = findViewById(R.id.clipText)
        empty = findViewById(R.id.empty)

        adapter = EntryAdapter()
        val list = findViewById<RecyclerView>(R.id.history)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<TextInputLayout>(R.id.urlLayout).setEndIconOnClickListener { paste() }
        findViewById<View>(R.id.playBtn).setOnClickListener { playFromInput() }
        findViewById<View>(R.id.fileBtn).setOnClickListener {
            try {
                filePicker.launch(arrayOf("video/*", "audio/*", "application/vnd.apple.mpegurl", "audio/x-mpegurl", "audio/mpegurl", "audio/x-scpls", "text/*", "application/octet-stream"))
            } catch (e: Exception) {
                Toast.makeText(this, "No file browser found on this device", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<View>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        advToggle.setOnClickListener {
            val show = headersLayout.visibility != View.VISIBLE
            headersLayout.visibility = if (show) View.VISIBLE else View.GONE
            advToggle.text = if (show) "− Hide request headers" else "+ Request headers (Referer, User-Agent…)"
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                playFromInput()
                true
            } else {
                false
            }
        }
        findViewById<View>(R.id.clipPlay).setOnClickListener {
            lastOffered = clipUrl
            clipCard.visibility = View.GONE
            play(clipUrl, "")
        }
        findViewById<View>(R.id.clipClose).setOnClickListener {
            lastOffered = clipUrl
            clipCard.visibility = View.GONE
        }

        handleSend(intent)
        offerCrashReport()
    }

    private fun offerCrashReport() {
        val f = java.io.File(filesDir, App.CRASH_FILE)
        if (!f.exists()) return
        val text = try { f.readText() } catch (e: Exception) { "" }
        f.delete()
        if (text.isBlank()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("StreamLite closed unexpectedly")
            .setMessage("Copy the error report so you can send it to the developer?")
            .setPositiveButton("Copy report") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("StreamLite crash", text))
                Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        adapter.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboard()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSend(intent)
    }

    private fun handleSend(i: Intent?) {
        if (i?.action == Intent.ACTION_PROCESS_TEXT) {
            // "Play in StreamLite" from the text-selection menu of any app
            val text = i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: return
            val link = LINK_RE.find(text)?.value
            if (link == null) {
                Toast.makeText(this, "No link found in the selected text", Toast.LENGTH_SHORT).show()
            } else {
                play(link, "")
            }
            return
        }
        if (i?.action == Intent.ACTION_SEND && i.type == "text/plain") {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            input.setText(LINK_RE.find(text)?.value ?: text.trim())
        }
    }

    private fun clipboardText(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun paste() {
        val text = clipboardText()?.trim()
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        } else {
            val url = LINK_RE.find(text)?.value ?: text
            input.setText(url)
            input.setSelection(url.length)
        }
    }

    private fun checkClipboard() {
        if (!prefAutoDetect) {
            clipCard.visibility = View.GONE
            return
        }
        val url = try {
            clipboardText()?.let { LINK_RE.find(it)?.value }
        } catch (e: Exception) {
            null
        }
        if (url == null || url == lastOffered || url == input.text?.toString()?.trim()) {
            if (url == null || url == lastOffered) clipCard.visibility = View.GONE
            return
        }
        clipUrl = url
        clipText.text = url
        clipCard.visibility = View.VISIBLE
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openLocal(uri)
    }

    /** A video, audio file or an .m3u/.m3u8/.pls list picked from the device. */
    private fun openLocal(uri: android.net.Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { /* some providers don't offer it */ }
        var name = ""
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: ""
            }
        } catch (e: Exception) { /* name is optional */ }
        val u = uri.toString()
        val e = Store.touch(this, u)
        if (name.isNotBlank() && e.title.isBlank()) e.title = name
        Store.update(this, e)
        val low = name.lowercase()
        val list = low.endsWith(".m3u") || low.endsWith(".m3u8") || low.endsWith(".pls")
        startActivity(
            Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, u)
                .putExtra(PlayerActivity.EXTRA_PLAYLIST, list)
        )
    }

    private fun playFromInput() {
        play(input.text?.toString() ?: "", headersInput.text?.toString() ?: "")
    }

    private fun play(raw: String, headers: String) {
        var u = raw.trim()
        if (u.isEmpty()) {
            input.error = "Enter a link"
            return
        }
        if (!u.contains("://")) u = "https://$u"
        val e = Store.touch(this, u)
        if (headers.isNotBlank()) e.headers = headers.trim()
        Store.update(this, e)
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, u))
    }

    private fun showMenu(e: Entry) {
        val labels = listOf(
            "Rename",
            if (e.pinned) "Remove from favorites" else "Add to favorites",
            "Request headers…",
            "Copy link",
            "Share link",
            "Remove"
        )
        Sheets.choose(this, Store.displayName(e), labels, -1) { i ->
            when (i) {
                0 -> Sheets.input(this, "Rename", "Name", e.title, false) {
                    e.title = it.trim()
                    Store.update(this, e)
                    adapter.refresh()
                }
                1 -> {
                    e.pinned = !e.pinned
                    Store.update(this, e)
                    adapter.refresh()
                }
                2 -> Sheets.input(this, "Request headers", "One per line, e.g. Referer: https://…", e.headers, true) {
                    e.headers = it.trim()
                    Store.update(this, e)
                }
                3 -> {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("link", e.url))
                    lastOffered = e.url
                    Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
                }
                4 -> startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, e.url),
                        "Share link"
                    )
                )
                5 -> {
                    Store.remove(this, e.url)
                    adapter.refresh()
                }
            }
        }
    }

    private inner class EntryAdapter : RecyclerView.Adapter<EntryAdapter.VH>() {
        private var items: List<Entry> = emptyList()

        fun refresh() {
            items = Store.sorted(this@MainActivity)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.itemTitle)
            val sub: TextView = v.findViewById(R.id.itemSub)
            val progress: LinearProgressIndicator = v.findViewById(R.id.itemProgress)
            val pin: ImageButton = v.findViewById(R.id.itemPin)
            val more: ImageButton = v.findViewById(R.id.itemMore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val e = items[position]
            h.title.text = Store.displayName(e)
            val hasPos = e.pos > 3000 && e.dur > 0
            val watched = e.dur > 0 && e.pos >= e.dur * 0.95
            h.sub.text = if (watched) {
                "Watched  \u00b7  ${Fmt.hostOf(e.url)}"
            } else if (hasPos) {
                "Resume at ${Fmt.time(e.pos)} of ${Fmt.time(e.dur)}  ·  ${Fmt.hostOf(e.url)}"
            } else {
                Fmt.hostOf(e.url)
            }
            h.progress.visibility = if (hasPos && !watched) View.VISIBLE else View.GONE
            if (hasPos) h.progress.progress = (e.pos * 1000 / e.dur).toInt().coerceIn(0, 1000)
            h.pin.setImageResource(if (e.pinned) R.drawable.ic_star else R.drawable.ic_star_border)
            h.pin.setColorFilter(
                if (e.pinned) Fmt.attr(this@MainActivity, androidx.appcompat.R.attr.colorPrimary)
                else Fmt.attr(this@MainActivity, R.attr.slText2)
            )
            h.pin.setOnClickListener {
                e.pinned = !e.pinned
                Store.update(this@MainActivity, e)
                refresh()
            }
            h.more.setOnClickListener { showMenu(e) }
            h.itemView.setOnClickListener { play(e.url, "") }
            h.itemView.setOnLongClickListener {
                showMenu(e)
                true
            }
        }
    }
}
