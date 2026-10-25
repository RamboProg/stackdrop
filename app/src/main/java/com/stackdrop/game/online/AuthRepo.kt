package com.stackdrop.game.online

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Firebase Auth: a silent anonymous account that can be upgraded to Google. */
class AuthRepo(private val auth: FirebaseAuth) {

    sealed class GoogleOutcome {
        /** The guest account became a Google account; same uid, nothing to migrate. */
        data object Linked : GoogleOutcome()

        /**
         * The Google account already existed, so the device switched to it; progress must be merged.
         * [guestToken] proves ownership of the guest account left behind, so the server can delete it;
         * null when it could not be fetched.
         */
        data class Switched(val guestToken: String?) : GoogleOutcome()
    }

    val uid: String? get() = auth.currentUser?.uid

    /** Signs in anonymously if nobody is signed in. Throws when offline; callers retry later. */
    suspend fun ensureSignedIn(): AccountSnapshot {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: error("Anonymous sign-in returned no user")
        return snapshot(user)
    }

    fun current(): AccountSnapshot = auth.currentUser?.let(::snapshot) ?: AccountSnapshot.SIGNED_OUT

    suspend fun signInWithGoogle(idToken: String): GoogleOutcome {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.currentUser
        var guestToken: String? = null
        if (user != null && user.isAnonymous) {
            try {
                user.linkWithCredential(credential).await()
                return GoogleOutcome.Linked
            } catch (e: FirebaseAuthUserCollisionException) {
                // This Google account already has its own progress; switch to it and merge. The
                // guest's token must be fetched now: after the switch the guest can't be reached.
                Log.i(TAG, "Google account already exists; switching to it", e)
                guestToken = guestTokenOf(user)
            }
        }
        auth.signInWithCredential(credential).await()
        return GoogleOutcome.Switched(guestToken)
    }

    fun signOut() = auth.signOut()

    private suspend fun guestTokenOf(user: FirebaseUser): String? = try {
        user.getIdToken(false).await().token
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't get the guest token; the guest account stays behind", e)
        null
    }

    private fun snapshot(user: FirebaseUser) = AccountSnapshot(
        uid = user.uid,
        anonymous = user.isAnonymous,
        email = user.email,
    )

    private companion object {
        const val TAG = "Auth"
    }
}
