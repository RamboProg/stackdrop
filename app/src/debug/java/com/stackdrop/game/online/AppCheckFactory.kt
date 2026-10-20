package com.stackdrop.game.online

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the App Check debug provider. On first launch it prints a debug secret to Logcat
 * ("Enter this debug secret into the allow list..."); register it in the Firebase console.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
