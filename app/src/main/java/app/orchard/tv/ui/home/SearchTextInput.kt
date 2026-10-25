package app.orchard.tv.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

internal fun Context.searchActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) break
        current = next
    }
    return current as? Activity
}

/** Pre-IME handling lets Up leave a TV input method without an extra Back press. */
internal class SearchTextInput(context: Context) : EditText(context) {
    var onQuery: (String) -> Unit = {}
    var onFinish: (Boolean) -> Unit = {}
    init {
        setSingleLine(true)
        isFocusableInTouchMode = true
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onQuery(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event == null || event.action == KeyEvent.ACTION_UP) onFinish(true)
                true
            } else false
        }
    }
    private fun exitKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_BACK) return false
        // Let the IME navigate uncommitted Chinese candidates before leaving for results.
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && BaseInputConnection.getComposingSpanStart(text) >= 0) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onFinish(keyCode == KeyEvent.KEYCODE_DPAD_UP)
        return true
    }
    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        context.searchActivity()?.onUserInteraction()
        return exitKey(keyCode, event) || super.onKeyPreIme(keyCode, event)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = exitKey(keyCode, event) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = exitKey(keyCode, event) || super.onKeyUp(keyCode, event)
    fun showKeyboard() {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
    fun hideKeyboard() {
        clearComposingText()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }
}
