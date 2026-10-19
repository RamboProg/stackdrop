package com.stackdrop.game.platform

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stackdrop.game.game.GameEvent

/** Runs bridge requests on the UI thread of [activity] and reports back through [events]. */
class ActivityBridge(private val activity: AppCompatActivity) : PlatformBridge {

    /** Set by the activity once the game exists. */
    var events: (GameEvent) -> Unit = {}

    override fun signInWithGoogle() = Unit

    override fun signOut() = Unit

    override fun deleteAccount() = Unit

    override fun showRewarded(placement: RewardPlacement) = Unit

    override fun maybeShowInterstitial() = Unit

    override fun showPrivacyOptions() = Unit

    override fun promptText(request: TextPrompt) = onUi {
        val input = EditText(activity).apply {
            setText(request.initial)
            setSelection(text.length)
            hint = request.hint
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = listOfNotNull(
                InputFilter.LengthFilter(request.maxLength),
                request.allowed?.let(::charFilter),
            ).toTypedArray()
        }
        val pad = (activity.resources.displayMetrics.density * 24).toInt()
        val frame = FrameLayout(activity).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        var answered = false
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(request.title)
            .setView(frame)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                answered = true
                events(GameEvent.TextEntered(request.id, input.text.toString().trim()))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { if (!answered) events(GameEvent.TextCancelled(request.id)) }
            .create()

        input.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    override fun share(text: String) = onUi {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(send, null))
    }

    override fun openUrl(url: String) = onUi {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            events(GameEvent.Toast("No browser found"))
        }
    }

    override fun exitToHome() = onUi { activity.moveTaskToBack(true) }

    private fun onUi(block: () -> Unit) = activity.runOnUiThread {
        if (!activity.isFinishing && !activity.isDestroyed) block()
    }

    private fun charFilter(allowed: Regex) = InputFilter { source, start, end, _, _, _ ->
        val kept = StringBuilder()
        for (i in start until end) {
            if (allowed.matches(source[i].toString())) kept.append(source[i])
        }
        if (kept.length == end - start) null else kept
    }
}
