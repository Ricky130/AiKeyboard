package com.example.aikeyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AiKeyboardService : InputMethodService() {

    private companion object {
        const val BG = 0xFF121212.toInt()
        const val KEY = 0xFF2A2A2A.toInt()
        const val FOCUS = 0xFF4F8CFF.toInt()
        const val REC = 0xFFD32F2F.toInt()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var editorInfo: EditorInfo? = null

    private var shift = false
    private var symbols = false

    private lateinit var keysContainer: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var micButton: Button
    private var toggleKey: Button? = null
    private val letterKeys = mutableListOf<Button>()

    private var recognizer: SpeechRecognizer? = null
    private var wantListening = false

    // ---------- UI ----------

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }

        statusView = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 13f
            text = "AI Keyboard"
        }
        root.addView(statusView)

        // Top row: mic + AI helpers
        val top = row()
        micButton = key("🎤 Mic", 2f) { toggleMic() }
        top.addView(micButton)
        top.addView(key("Explain", 1.3f, 14f) { insert("Explain this simply: ") })
        top.addView(key("Summarize", 1.4f, 14f) { insert("Summarize this: ") })
        top.addView(key("Steps", 1.1f, 14f) { insert("Think step by step. ") })
        top.addView(key("Clear", 1.1f, 14f) { clearAll() })
        top.addView(key("⌫ Word", 1.3f, 14f) { deleteWord() })
        root.addView(top)

        keysContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keysContainer)

        buildKeys()
        return root
    }

    private fun buildKeys() {
        keysContainer.removeAllViews()
        letterKeys.clear()

        val rows = if (symbols) {
            listOf("1234567890", "@#\$%&-+()/", "*\"':;!?=_~")
        } else {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }

        for ((i, chars) in rows.withIndex()) {
            val r = row()
            if (i == 2 && !symbols) {
                r.addView(key("⇧", 1.5f) { shift = !shift; updateCase() })
            }
            for (c in chars) {
                val b = key(c.toString()) { typeChar(c) }
                if (!symbols) letterKeys.add(b)
                r.addView(b)
            }
            if (i == 2) r.addView(key("⌫", 1.5f) { backspace() })
            keysContainer.addView(r)
        }

        val bottom = row()
        toggleKey = key(if (symbols) "ABC" else "?123", 1.4f, 15f) {
            symbols = !symbols
            buildKeys()
            toggleKey?.post { toggleKey?.requestFocus() }
        }
        bottom.addView(toggleKey)
        bottom.addView(key("◀", 1f) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) })
        bottom.addView(key(",", 1f) { typeChar(',') })
        bottom.addView(key("space", 4f, 15f) { currentInputConnection?.commitText(" ", 1) })
        bottom.addView(key(".", 1f) { typeChar('.') })
        bottom.addView(key("▶", 1f) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) })
        bottom.addView(key("⏎", 1f) { currentInputConnection?.commitText("\n", 1) })
        bottom.addView(key("Send", 1.6f, 15f) { sendAction() })
        keysContainer.addView(bottom)

        updateCase()
    }

    private fun updateCase() {
        for (b in letterKeys) {
            val c = b.text.toString()
            b.text = if (shift) c.uppercase() else c.lowercase()
        }
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun key(label: String, weight: Float = 1f, size: Float = 18f, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = size
            setTextColor(Color.WHITE)
            background = keyBg(KEY)
            isFocusable = true          // d-pad / remote navigation
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }   // DPAD_CENTER / ENTER triggers this
            layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        }

    private fun keyBg(normal: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(FOCUS))
        addState(intArrayOf(android.R.attr.state_pressed), rounded(FOCUS))
        addState(intArrayOf(), rounded(normal))
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(color)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- Text actions ----------

    private fun typeChar(c: Char) {
        val s = if (!symbols && shift) c.uppercaseChar().toString() else c.toString()
        currentInputConnection?.commitText(s, 1)
        if (shift) {
            shift = false
            updateCase()
        }
    }

    private fun insert(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (sel.isNullOrEmpty()) ic.deleteSurroundingText(1, 0) else ic.commitText("", 1)
    }

    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: return
        if (before.isEmpty()) return
        val trimmed = before.trimEnd()
        val remove = before.length - (trimmed.lastIndexOf(' ') + 1)
        ic.deleteSurroundingText(remove, 0)
    }

    private fun clearAll() {
        val ic = currentInputConnection ?: return
        ic.performContextMenuAction(android.R.id.selectAll)
        ic.commitText("", 1)
    }

    private fun sendAction() {
        val ic = currentInputConnection ?: return
        val info = editorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnter = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    // ---------- Lifecycle ----------

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editorInfo = info
        if (::micButton.isInitialized) micButton.post { micButton.requestFocus() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (wantListening) stopListening()
        currentInputConnection?.finishComposingText()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    // ---------- Voice dictation (speech-to-text) ----------

    private fun toggleMic() {
        if (wantListening) stopListening() else startListening()
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Microphone permission needed — opening setup")
            startActivity(
                Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("No speech recognition service on this device")
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(listener)
            }
        }
        wantListening = true
        updateMic()
        beginRecognition()
    }

    private fun beginRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun stopListening() {
        wantListening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        updateMic()
        setStatus("AI Keyboard")
    }

    private fun restartSoon() {
        handler.postDelayed({ if (wantListening) beginRecognition() }, 300)
    }

    private fun updateMic() {
        if (!::micButton.isInitialized) return
        micButton.text = if (wantListening) "■ Stop" else "🎤 Mic"
        micButton.background = keyBg(if (wantListening) REC else KEY)
    }

    private fun setStatus(s: String) {
        if (::statusView.isInitialized) statusView.text = s
    }

    private fun firstResult(b: Bundle?): String =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = setStatus("Listening… (press Mic again to stop)")
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = firstResult(partialResults)
            if (t.isNotEmpty()) currentInputConnection?.setComposingText(t, 1)
        }

        override fun onResults(results: Bundle?) {
            val t = firstResult(results)
            if (t.isNotEmpty()) currentInputConnection?.commitText("$t ", 1)
            if (wantListening) restartSoon()
        }

        override fun onError(error: Int) {
            if (!wantListening) {
                currentInputConnection?.finishComposingText()
                return
            }
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                restartSoon()   // keep dictating through pauses
            } else {
                setStatus("Mic error ($error)")
                stopListening()
            }
        }
    }
}
