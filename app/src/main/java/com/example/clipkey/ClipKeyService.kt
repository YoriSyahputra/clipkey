package com.example.clipkey

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.clipkey.data.AppDatabase
import com.example.clipkey.data.ClipEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ClipKeyService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var database: AppDatabase

    private var containerKeyboard: LinearLayout? = null
    private var containerClipboardPanel: LinearLayout? = null
    private var layoutClipsQuick: LinearLayout? = null
    private var layoutClipsList: LinearLayout? = null
    private var etSearchClip: EditText? = null

    private var isCaps = false
    private val letterButtons = mutableListOf<Button>()
    private var currentFilterPinnedOnly = false
    private var currentSearchQuery = ""

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        database = AppDatabase.getInstance(applicationContext)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)

        containerKeyboard = view.findViewById(R.id.containerKeyboard)
        containerClipboardPanel = view.findViewById(R.id.containerClipboardPanel)
        layoutClipsQuick = view.findViewById(R.id.layoutClipsQuick)
        layoutClipsList = view.findViewById(R.id.layoutClipsList)
        etSearchClip = view.findViewById(R.id.etSearchClip)

        val btnOpenClipboard = view.findViewById<Button>(R.id.btnOpenClipboard)
        val btnCloseClipboard = view.findViewById<Button>(R.id.btnCloseClipboard)
        val btnFilterAll = view.findViewById<Button>(R.id.btnFilterAll)
        val btnFilterPinned = view.findViewById<Button>(R.id.btnFilterPinned)

        btnOpenClipboard.setOnClickListener {
            showClipboardPanel(true)
        }

        btnCloseClipboard.setOnClickListener {
            showClipboardPanel(false)
        }

        btnFilterAll.setOnClickListener {
            currentFilterPinnedOnly = false
            reloadClips()
        }

        btnFilterPinned.setOnClickListener {
            currentFilterPinnedOnly = true
            reloadClips()
        }

        etSearchClip?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                reloadClips()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupQwertyKeys()
        reloadClips()

        return view
    }

    private fun showClipboardPanel(show: Boolean) {
        containerClipboardPanel?.visibility = if (show) View.VISIBLE else View.GONE
        containerKeyboard?.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        captureClipboard()
        showClipboardPanel(false)
    }

    private fun setupQwertyKeys() {
        val container = containerKeyboard ?: return
        container.removeAllViews()
        letterButtons.clear()

        val rows = listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            listOf("123", ",", "SPACE", ".", "↵")
        )

        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
            }

            for (key in row) {
                val btn = Button(this).apply {
                    text = key
                    textSize = 16f
                    setTextColor(Color.parseColor("#CDD6F4"))
                    background = ContextCompat.getDrawable(this@ClipKeyService, R.drawable.key_bg)
                    isAllCaps = false

                    val weight = when (key) {
                        "SPACE" -> 5.0f
                        "⇧", "⌫", "123", "↵" -> 1.5f
                        else -> 1.0f
                    }

                    val params = LinearLayout.LayoutParams(0, dpToPx(48), weight).apply {
                        setMargins(dpToPx(2), dpToPx(3), dpToPx(2), dpToPx(3))
                    }
                    layoutParams = params

                    if (key !in listOf("SPACE", "⇧", "⌫", "123", "↵", ",", ".")) {
                        letterButtons.add(this)
                    }

                    setOnClickListener {
                        handleKeyPress(key, this)
                    }
                }
                rowLayout.addView(btn)
            }
            container.addView(rowLayout)
        }
    }

    private fun handleKeyPress(key: String, button: Button) {
        val ic = currentInputConnection ?: return
        when (key) {
            "⌫" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            "↵" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            "SPACE" -> ic.commitText(" ", 1)
            "⇧" -> toggleShift()
            "123" -> {}
            else -> ic.commitText(button.text.toString(), 1)
        }
    }

    private fun toggleShift() {
        isCaps = !isCaps
        for (btn in letterButtons) {
            val current = btn.text.toString()
            btn.text = if (isCaps) current.uppercase() else current.lowercase()
        }
    }

    private fun captureClipboard() {
        if (!clipboardManager.hasPrimaryClip()) return
        val clip = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        if (clip.isBlank()) return

        serviceScope.launch(Dispatchers.IO) {
            val exists = database.clipDao().findByContent(clip)
            if (exists == null) {
                database.clipDao().insert(ClipEntity(content = clip))
            }
        }
    }

    private fun reloadClips() {
        serviceScope.launch {
            val flow = when {
                currentSearchQuery.isNotEmpty() -> database.clipDao().searchClips(currentSearchQuery)
                currentFilterPinnedOnly -> database.clipDao().getPinnedClips()
                else -> database.clipDao().getAllClips()
            }
            flow.collectLatest { clips ->
                updateQuickToolbarClips(clips)
                updatePanelClipsList(clips)
            }
        }
    }

    private fun updateQuickToolbarClips(clips: List<ClipEntity>) {
        val container = layoutClipsQuick ?: return
        container.removeAllViews()

        for (clip in clips.take(10)) {
            val btn = Button(this).apply {
                text = if (clip.content.length > 18) clip.content.take(18) + "…" else clip.content
                textSize = 11f
                setTextColor(Color.parseColor("#BAC2DE"))
                background = ContextCompat.getDrawable(this@ClipKeyService, R.drawable.chip_bg)
                isAllCaps = false
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(32)
                ).apply {
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }
                layoutParams = params

                setOnClickListener {
                    currentInputConnection?.commitText(clip.content, 1)
                }
            }
            container.addView(btn)
        }
    }

    private fun updatePanelClipsList(clips: List<ClipEntity>) {
        val container = layoutClipsList ?: return
        container.removeAllViews()

        for (clip in clips) {
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dpToPx(4), 0, dpToPx(4))
                }
                setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8))
                background = ContextCompat.getDrawable(this@ClipKeyService, R.drawable.key_bg)
            }

            // 1. Ikon Pin di Kiri
            val btnPin = Button(this).apply {
                text = "📌"
                textSize = 14f
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                setTextColor(if (clip.isPinned) Color.parseColor("#89B4FA") else Color.parseColor("#6C7086"))
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
                setOnClickListener {
                    serviceScope.launch(Dispatchers.IO) {
                        database.clipDao().updatePinStatus(clip.id, !clip.isPinned)
                    }
                }
            }

            // 2. Teks Klip di Tengah (Klik untuk paste)
            val tvText = TextView(this).apply {
                text = clip.content
                textSize = 13f
                setTextColor(Color.parseColor("#CDD6F4"))
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(dpToPx(6), 0, dpToPx(6), 0)
                }
                setOnClickListener {
                    currentInputConnection?.commitText(clip.content, 1)
                    showClipboardPanel(false)
                }
            }

            // 3. Ikon Sampah di Kanan
            val btnDelete = Button(this).apply {
                text = "🗑"
                textSize = 14f
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                setTextColor(Color.parseColor("#F38BA8"))
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
                setOnClickListener {
                    serviceScope.launch(Dispatchers.IO) {
                        database.clipDao().deleteById(clip.id)
                    }
                }
            }

            itemRow.addView(btnPin)
            itemRow.addView(tvText)
            itemRow.addView(btnDelete)
            container.addView(itemRow)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
