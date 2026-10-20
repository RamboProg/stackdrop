package com.stackdrop.game.online

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Firebase Auth: a silent anonymous account that can be upgraded to Google. */
class AuthRepo(private val auth: FirebaseAuth) {

    enum class GoogleOutcome {
        /** The guest account became a Google account; same uid, nothing to migrate. */
        LINKED,

        /** The Google account already existed, so the device switched to it; progress must be merged. */
        SWITCHED,
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
        if (user != null && user.isAnonymous) {
            try {
                user.linkWithCredential(credential).await()
                return GoogleOutcome.LINKED
            } catch (e: FirebaseAuthUserCollisionException) {
                // This Google account already has its own progress; switch to it and merge.
            }
        }
        auth.signInWithCredential(credential).await()
        return GoogleOutcome.SWITCHED
    }

    fun signOut() = auth.signOut()

    private fun snapshot(user: FirebaseUser) = AccountSnapshot(
        uid = user.uid,
        anonymous = user.isAnonymous,
        email = user.email,
    )
}
