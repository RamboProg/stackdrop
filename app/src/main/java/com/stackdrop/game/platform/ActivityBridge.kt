package com.stackdrop.game.platform

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stackdrop.game.ads.AdsManager
import com.stackdrop.game.ads.ConsentManager
import com.stackdrop.game.game.GameEvent
import com.stackdrop.game.online.OnlineServices
import kotlinx.coroutines.launch

/** Runs bridge requests on the UI thread of [activity] and reports back through [events]. */
class ActivityBridge(
    private val activity: AppCompatActivity,
    private val online: OnlineServices,
    private val ads: AdsManager,
    private val consent: ConsentManager,
) : PlatformBridge {

    /** Set by the activity once the game exists. */
    var events: (GameEvent) -> Unit = {}

    @Volatile
    private var privacyRequired = false

    /** Asks for consent on launch where required, then starts loading ads. Never shows an ad. */
    fun startAds() {
        consent.gather {
            privacyRequired = consent.privacyOptionsRequired
            ads.start()
        }
    }

    override fun showRewarded(placement: RewardPlacement) {
        ads.showRewarded(placement)
    }

    override fun maybeShowInterstitial() {
        ads.showInterstitial()
    }

    override fun showPrivacyOptions() = onUi {
        consent.showPrivacyOptions { privacyRequired = consent.privacyOptionsRequired }
    }

    override fun rewardedReady() = ads.rewardedReady

    override fun interstitialReady() = ads.interstitialReady

    override fun privacyOptionsRequired() = privacyRequired

    /** Shows Google's account picker, then hands the ID token to Firebase. */
    override fun signInWithGoogle() = onUi {
        if (!online.available) {
            events(GameEvent.Toast(NOT_SET_UP))
            return@onUi
        }
        val clientId = webClientId()
        if (clientId == null) {
            events(GameEvent.Toast(NOT_SET_UP))
            return@onUi
        }
        activity.lifecycleScope.launch {
            try {
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                    .build()
                val credential = CredentialManager.create(activity).getCredential(activity, request).credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    online.completeGoogleSignIn(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                } else {
                    events(GameEvent.AuthFailed(COULDNT_SIGN_IN))
                }
            } catch (e: GetCredentialCancellationException) {
                // The player backed out of the picker; nothing to say.
                Log.d(TAG, "Google sign-in cancelled", e)
            } catch (e: NoCredentialException) {
                events(GameEvent.AuthFailed("No Google account on this device"))
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Credential Manager failed", e)
                events(GameEvent.AuthFailed(COULDNT_SIGN_IN))
            } catch (e: GoogleIdTokenParsingException) {
                Log.w(TAG, "Bad Google ID token", e)
                events(GameEvent.AuthFailed(COULDNT_SIGN_IN))
            }
        }
    }

    override fun confirm(request: ConfirmPrompt) = onUi {
        MaterialAlertDialogBuilder(activity)
            .setTitle(request.title)
            .setMessage(request.message)
            .setPositiveButton(request.confirmLabel) { _, _ -> events(GameEvent.Confirmed(request.id)) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun signOut() = onUi {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Sign out?")
            .setMessage("Progress on this phone stays here, but it stops syncing to your Google account.")
            .setPositiveButton("Sign out") { _, _ -> online.signOut() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Two confirmations: deleting is permanent and removes the player's data everywhere. */
    override fun deleteAccount() = onUi {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Delete your account?")
            .setMessage(
                "This permanently deletes your name, friend code, friends, ranks and cloud save. " +
                    "Progress on this phone is reset too; only your settings are kept."
            )
            .setPositiveButton("Continue") { _, _ -> confirmDeleteAgain() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAgain() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Are you sure?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete forever") { _, _ ->
                events(GameEvent.Toast("Deleting your account…"))
                online.deleteAccount()
            }
            .setNegativeButton("Keep my account", null)
            .show()
    }

    override fun copyToClipboard(text: String) = onUi {
        val clipboard = activity.getSystemService(ClipboardManager::class.java) ?: return@onUi
        clipboard.setPrimaryClip(ClipData.newPlainText("Stack Drop friend code", text))
        // Android 13+ confirms copies itself; older versions get our banner.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) events(GameEvent.Toast("Copied"))
    }

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

    // Looked up by name: the string is generated from google-services.json, which may be absent.
    @SuppressLint("DiscouragedApi")
    private fun webClientId(): String? {
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        return if (id == 0) null else activity.getString(id)
    }

    private companion object {
        const val TAG = "ActivityBridge"
        const val COULDNT_SIGN_IN = "Couldn't sign in. Try again"
        const val NOT_SET_UP = "Google sign-in isn't set up yet"
    }
}
