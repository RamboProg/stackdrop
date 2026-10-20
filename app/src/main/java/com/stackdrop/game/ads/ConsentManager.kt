package com.stackdrop.game.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.stackdrop.game.BuildConfig

/**
 * Google's User Messaging Platform: asks for consent where the law requires it (the message is set
 * up in AdMob → Privacy & messaging) before any ad is requested. UI thread only.
 */
class ConsentManager(private val activity: Activity) {

    private val info = UserMessagingPlatform.getConsentInformation(activity)

    val privacyOptionsRequired: Boolean
        get() = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Updates consent, shows the form if needed, and calls [onReady] once ads may be requested. */
    fun gather(onReady: () -> Unit) {
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false)
        if (BuildConfig.DEBUG) {
            // Debug builds always act as if the device were in the EEA, so the form can be tested.
            params.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .setForceTesting(true)
                    .build()
            )
        }
        info.requestConsentInfoUpdate(
            activity,
            params.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.errorCode} ${error.message}")
                    if (info.canRequestAds()) onReady()
                }
            },
            { error ->
                Log.w(TAG, "Consent update failed: ${error.errorCode} ${error.message}")
                if (info.canRequestAds()) onReady()
            },
        )
        // Consent given in an earlier session lets ads load without waiting for the update.
        if (info.canRequestAds()) onReady()
    }

    fun showPrivacyOptions(onClosed: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.errorCode} ${error.message}")
            onClosed()
        }
    }

    private companion object {
        const val TAG = "Consent"
    }
}
