package com.example.aikeyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Simple setup screen. Every button is focusable, so it works with a TV remote. */
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun addText(t: String) {
            col.addView(TextView(this).apply {
                text = t
                textSize = 18f
                setPadding(0, pad / 2, 0, pad / 2)
            })
        }

        fun addButton(label: String, action: () -> Unit) {
            col.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            })
        }

        addText("AI Keyboard setup")
        addButton("1. Enable AI Keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        addButton("2. Grant microphone permission") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        addButton("3. Switch to AI Keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        addText(
            "If your TV hides these settings, use adb:\n" +
                "adb shell ime enable com.example.aikeyboard/.AiKeyboardService\n" +
                "adb shell ime set com.example.aikeyboard/.AiKeyboardService\n" +
                "adb shell pm grant com.example.aikeyboard android.permission.RECORD_AUDIO"
        )
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
