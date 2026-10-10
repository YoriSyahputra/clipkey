package com.example.clipkey

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.example.clipkey.data.AppDatabase
import com.example.clipkey.data.ClipEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClipKeyService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var database: AppDatabase
    private var clipsLayout: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        database = AppDatabase.getInstance(applicationContext)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        clipsLayout = view.findViewById(R.id.layoutClips)

        val btnSync = view.findViewById<Button>(R.id.btnRefreshClip)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)

        btnSync.setOnClickListener {
            captureClipboard()
        }

        btnDelete.setOnClickListener {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

        observeClips()
        return view
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        captureClipboard()
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
                text = if (clip.content.length > 20) clip.content.take(20) + "..." else clip.content
                textSize = 12f
                isAllCaps = false
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
