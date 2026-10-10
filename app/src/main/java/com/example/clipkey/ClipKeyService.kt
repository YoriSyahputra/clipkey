package com.example.clipkey

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
    
    private var clipsLayout: LinearLayout? = null
    private var keyboardRowsLayout: LinearLayout? = null
    
    private var isCaps = false
    private val letterButtons = mutableListOf<Button>()

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        database = AppDatabase.getInstance(applicationContext)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        clipsLayout = view.findViewById(R.id.layoutClips)
        keyboardRowsLayout = view.findViewById(R.id.layoutKeyboardRows)

        setupQwertyKeys()
        observeClips()

        return view
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Otomatis tangkap clipboard terbaru setiap kali keyboard terbuka
        captureClipboard()
    }

    private fun setupQwertyKeys() {
        val container = keyboardRowsLayout ?: return
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
                weightSum = if (row.contains("SPACE")) 10f else row.size.toFloat()
            }

            for (key in row) {
                val btn = Button(this).apply {
                    text = key
                    textSize = 16f
                    setTextColor(0xFFCDD6F4.toInt())
                    background = ContextCompat.getDrawable(this@ClipKeyService, R.drawable.key_bg)
                    isAllCaps = false
                    
                    val params = LinearLayout.LayoutParams(0, 140) // tinggi tombol proporsional
                    params.setMargins(4, 4, 4, 4)

                    when (key) {
                        "SPACE" -> {
                            params.weight = 5f
                            text = " "
                        }
                        "⇧", "⌫", "123", "↵" -> {
                            params.weight = 1.5f
                        }
                        else -> {
                            params.weight = 1f
                            letterButtons.add(this)
                        }
                    }
                    layoutParams = params

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
            "123" -> {
                // Placeholder mode angka
            }
            else -> {
                val textToCommit = button.text.toString()
                ic.commitText(textToCommit, 1)
            }
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

    private fun observeClips() {
        serviceScope.launch {
            database.clipDao().getAllClips().collectLatest { clips ->
                updateClipUI(clips)
            }
        }
    }

    private fun updateClipUI(clips: List<ClipEntity>) {
        val container = clipsLayout ?: return
        container.removeAllViews()

        for (clip in clips) {
            val btn = Button(this).apply {
                val display = if (clip.content.length > 25) clip.content.take(25) + "…" else clip.content
                text = display
                textSize = 12f
                setTextColor(0xFFBAC2DE.toInt())
                background = ContextCompat.getDrawable(this@ClipKeyService, R.drawable.chip_bg)
                isAllCaps = false
                
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(6, 4, 6, 4)
                layoutParams = params

                setOnClickListener {
                    currentInputConnection?.commitText(clip.content, 1)
                }
            }
            container.addView(btn)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
