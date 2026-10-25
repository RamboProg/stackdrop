package com.stackdrop.game.online

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.stackdrop.game.BuildConfig
import com.stackdrop.game.data.PendingRun
import com.stackdrop.game.game.GameEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** What the game thread may ask of the online layer. Calls never block and never throw. */
interface OnlineApi {
    /** False when this build has no Firebase config; everything online is then hidden. */
    val available: Boolean

    fun queuePush(p: CloudPush)
    fun pushNow(p: CloudPush)

    /**
     * Saves [final] to the cloud, then signs out; the game resets the phone on SignedOut. When the
     * save can't be written the player stays signed in, so no progress is stranded on the phone.
     */
    fun signOut(final: CloudPush?)
    fun claimName(name: String, avatar: Int)
    fun setAvatar(avatar: Int)

    /** Posts the cached board right away when there is one, then a fresh copy unless it is under a minute old. */
    fun loadBoard(id: String)

    /** Marks the start of a run on the server. Offline, the run can't be ranked later. */
    fun startRun(runId: String, modeId: String)

    /** Sends queued runs one at a time; each result comes back as RunSubmitted or RunDropped. */
    fun submitRuns(runs: List<PendingRun>)

    /** Friends and incoming requests, posted as FriendsLoaded. */
    fun loadFriends()

    /** By typed code or by uid (from a leaderboard row); the outcome is posted as a Toast. */
    fun sendFriendRequest(code: String?, uid: String?)
    fun respondFriendRequest(fromUid: String, accept: Boolean)
    fun removeFriend(uid: String)

    /** The all-time board for [modeId] limited to friends and the player. */
    fun loadFriendsBoard(modeId: String, friendIds: List<String>)

    /** Another player's profile, posted as PlayerLoaded. */
    fun loadPlayer(uid: String)
}

/**
 * Owns the Firebase singletons and the coroutine scope for all network work. Results go back to the
 * game thread only as [GameEvent]s. With no `google-services.json` in the build, or no network, the
 * game keeps working offline and this layer quietly retries on the next resume.
 */
class OnlineServices(context: Context) : OnlineApi {

    /** Set by the activity once the game exists. */
    @Volatile
    var events: (GameEvent) -> Unit = {}

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override val available = FirebaseApp.getApps(context).isNotEmpty()

    private val auth: AuthRepo?
    private val cloud: CloudSave?
    private val profiles: ProfileRepo?
    private val boards: LeaderboardRepo?
    private val friends: FriendsRepo?

    @Volatile
    private var synced = false
    private var syncJob: Job? = null
    private var avatarJob: Job? = null
    private var submitJob: Job? = null

    init {
        if (available) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
            val firebaseAuth = FirebaseAuth.getInstance()
            val db = FirebaseFirestore.getInstance()
            val functions = FirebaseFunctions.getInstance(REGION)
            // The SDKs allow this once per process, and the activity may be recreated.
            if (BuildConfig.USE_EMULATORS && emulatorsWired.compareAndSet(false, true)) {
                firebaseAuth.useEmulator(BuildConfig.EMULATOR_HOST, AUTH_PORT)
                db.useEmulator(BuildConfig.EMULATOR_HOST, FIRESTORE_PORT)
                functions.useEmulator(BuildConfig.EMULATOR_HOST, FUNCTIONS_PORT)
            }
            auth = AuthRepo(firebaseAuth)
            cloud = CloudSave(db, auth, scope) { uid, written -> events(GameEvent.CloudPushed(uid, written)) }
            profiles = ProfileRepo(db, functions)
            boards = LeaderboardRepo(db, functions, File(appContext.filesDir, BOARD_CACHE_DIR))
            friends = FriendsRepo(db, functions)
        } else {
            Log.i(TAG, "No Firebase config in this build; playing offline")
            auth = null
            cloud = null
            profiles = null
            boards = null
            friends = null
        }
    }

    /**
     * Signs in silently and pulls the cloud save once per process, retried on every resume until it
     * works. Later resumes refresh friends so the menu badge shows new requests.
     */
    fun onResume() {
        if (!available) return
        if (synced) {
            loadFriends()
            return
        }
        synchronized(this) {
            if (syncJob?.isActive == true) return
            syncJob = scope.launch { runLogged("Sign-in or cloud pull") { syncAccount(signedInAccount()) } }
        }
    }

    override fun queuePush(p: CloudPush) {
        cloud?.queuePush(p)
    }

    override fun pushNow(p: CloudPush) {
        cloud?.pushNow(p)
    }

    override fun claimName(name: String, avatar: Int) {
        val repo = profiles ?: return
        scope.launch {
            val uid = auth?.uid
            if (uid == null) {
                events(GameEvent.NameRejected(OFFLINE))
                return@launch
            }
            try {
                when (val r = repo.claimName(name, avatar)) {
                    ProfileRepo.Claim.Ok -> events(GameEvent.ProfileLoaded(repo.load(uid)))
                    is ProfileRepo.Claim.Refused -> events(GameEvent.NameRejected(NameRules.message(r.reason, r.suggestions)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "claimName failed", e)
                events(GameEvent.NameRejected(NameRules.message("error", emptyList())))
            }
        }
    }

    /** Debounced, so tapping through the avatars sends only the one the player settles on. */
    override fun setAvatar(avatar: Int) {
        val repo = profiles ?: return
        synchronized(this) {
            avatarJob?.cancel()
            avatarJob = scope.launch {
                delay(AVATAR_DEBOUNCE_MS)
                try {
                    repo.setAvatar(avatar)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "setAvatar failed", e)
                    events(GameEvent.Toast("Couldn't save your avatar"))
                }
            }
        }
    }

    override fun loadBoard(id: String) {
        val repo = boards ?: return
        scope.launch {
            val now = System.currentTimeMillis()
            repo.fresh(id, now)?.let {
                events(GameEvent.BoardLoaded(it))
                return@launch
            }
            val cached = repo.cached(id)
            if (cached != null) events(GameEvent.BoardLoaded(cached))
            try {
                events(GameEvent.BoardLoaded(repo.fetch(id, auth?.uid, now)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "Board $id unavailable; showing saved copy", e)
                events(GameEvent.BoardLoaded(cached?.copy(offline = true) ?: BoardSnapshot.offlineEmpty(id)))
            }
        }
    }

    override fun startRun(runId: String, modeId: String) {
        val repo = boards ?: return
        if (auth?.uid == null) return
        scope.launch { runLogged("Run start") { repo.startRun(runId, modeId) } }
    }

    override fun submitRuns(runs: List<PendingRun>) {
        val repo = boards ?: return
        if (runs.isEmpty() || auth?.uid == null) return
        synchronized(this) {
            if (submitJob?.isActive == true) return
            submitJob = scope.launch {
                for ((i, run) in runs.withIndex()) {
                    // submitScore allows one run every 5 seconds per player.
                    if (i > 0) delay(SUBMIT_GAP_MS)
                    when (val r = repo.submit(run)) {
                        is LeaderboardRepo.Submit.Accepted -> events(
                            GameEvent.RunSubmitted(run.runId, r.newAllTime, r.newWeekly, r.weeklyRank)
                        )
                        is LeaderboardRepo.Submit.Rejected -> events(GameEvent.RunDropped(run.runId))
                        LeaderboardRepo.Submit.Retry -> return@launch
                    }
                }
            }
        }
    }

    override fun loadFriends() {
        val repo = friends ?: return
        val uid = auth?.uid ?: return
        scope.launch { runLogged("Friends load") { events(GameEvent.FriendsLoaded(repo.load(uid))) } }
    }

    override fun sendFriendRequest(code: String?, uid: String?) {
        friendAction("Friend request") { repo -> events(GameEvent.Toast(repo.sendRequest(code, uid))) }
    }

    override fun respondFriendRequest(fromUid: String, accept: Boolean) {
        friendAction("Friend response") { repo -> repo.respond(fromUid, accept) }
    }

    override fun removeFriend(uid: String) {
        friendAction("Remove friend") { repo -> repo.remove(uid) }
    }

    override fun loadFriendsBoard(modeId: String, friendIds: List<String>) {
        val repo = friends ?: return
        val uid = auth?.uid ?: return
        val id = FriendsRepo.friendsBoardId(modeId)
        scope.launch {
            try {
                events(GameEvent.BoardLoaded(repo.board(modeId, uid, friendIds, System.currentTimeMillis())))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "Friends board unavailable", e)
                events(GameEvent.BoardLoaded(BoardSnapshot.offlineEmpty(id)))
            }
        }
    }

    override fun loadPlayer(uid: String) {
        val repo = profiles ?: return
        scope.launch { runLogged("Player load") { repo.load(uid)?.let { events(GameEvent.PlayerLoaded(it)) } } }
    }

    /** Runs a friends change, reports failures gently, then reloads the list either way. */
    private fun friendAction(what: String, block: suspend (FriendsRepo) -> Unit) {
        val repo = friends ?: return
        val uid = auth?.uid ?: return
        scope.launch {
            try {
                block(repo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$what failed", e)
                events(GameEvent.Toast("You're offline. Try again in a moment"))
            }
            runLogged("Friends reload") { events(GameEvent.FriendsLoaded(repo.load(uid))) }
        }
    }

    /** Called by the activity once Credential Manager returned a Google ID token. */
    fun completeGoogleSignIn(idToken: String) {
        val repo = auth ?: return
        scope.launch {
            val guestUid = repo.uid
            val outcome = try {
                repo.signInWithGoogle(idToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Google sign-in failed", e)
                events(GameEvent.AuthFailed("Couldn't sign in. Try again"))
                return@launch
            }
            events(GameEvent.Toast("Signed in with Google"))
            when (outcome) {
                AuthRepo.GoogleOutcome.Linked -> events(GameEvent.AuthChanged(repo.current()))
                is AuthRepo.GoogleOutcome.Switched -> {
                    // Nothing may be saved until this account's cloud copy has been merged in.
                    synced = false
                    guestUid?.let { cloud?.forget(it) }
                    runLogged("Cloud pull after switching accounts") { syncAccount(repo.current()) }
                    outcome.guestToken?.let { deleteGuest(it) }
                }
            }
        }
    }

    /**
     * Data deletion: the guest account a Google sign-in switched away from. Its progress was merged
     * into the Google account on this phone; left alone, its name, friends and ranks would stay in
     * an account nobody can reach or delete.
     */
    private suspend fun deleteGuest(guestToken: String) {
        runLogged("Deleting the guest account left behind") { profiles?.deleteGuest(guestToken) }
    }

    override fun signOut(final: CloudPush?) {
        val repo = auth ?: return
        val save = cloud ?: return
        scope.launch {
            if (final != null) {
                try {
                    save.write(final)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Final save before sign-out failed", e)
                    events(GameEvent.Toast("Couldn't save to your account. Try again when you're online"))
                    return@launch
                }
                save.forget(final.uid)
            }
            repo.signOut()
            clearCredentials()
            synced = false
            events(GameEvent.SignedOut)
            events(GameEvent.ProfileLoaded(null))
            events(GameEvent.AuthChanged(AccountSnapshot.SIGNED_OUT))
            runLogged("Guest sign-in after sign-out") { syncAccount(signedInAccount()) }
        }
    }

    /** Deletes the account and all of its cloud data, then starts over as a fresh guest. */
    fun deleteAccount() {
        val repo = profiles ?: return
        val authRepo = auth ?: return
        scope.launch {
            // Stop saving first, so no write can recreate the cloud save after it is deleted.
            val uid = authRepo.uid
            uid?.let { cloud?.forget(it) }
            try {
                repo.deleteAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "deleteAccount failed", e)
                uid?.let { cloud?.remember(it) }
                events(GameEvent.Toast("Couldn't delete. Check your connection"))
                return@launch
            }
            authRepo.signOut()
            clearCredentials()
            synced = false
            events(GameEvent.AccountDeleted)
            events(GameEvent.ProfileLoaded(null))
            events(GameEvent.AuthChanged(AccountSnapshot.SIGNED_OUT))
            runLogged("Guest sign-in after deletion") { syncAccount(signedInAccount()) }
        }
    }

    fun close() = scope.cancel()

    private suspend fun signedInAccount(): AccountSnapshot = auth?.ensureSignedIn() ?: AccountSnapshot.SIGNED_OUT

    /** Announces [account], merges its cloud save and loads its profile. */
    private suspend fun syncAccount(account: AccountSnapshot) {
        events(GameEvent.AuthChanged(account))
        val uid = account.uid ?: return
        val save = cloud ?: return
        val remote = save.pull(uid)
        // Signed back in to an account this session signed out of: it may be saved again.
        save.remember(uid)
        synced = true
        events(GameEvent.CloudLoaded(uid, remote))
        runLogged("Profile load") { events(GameEvent.ProfileLoaded(profiles?.load(uid))) }
        runLogged("Friends load") { friends?.let { events(GameEvent.FriendsLoaded(it.load(uid))) } }
    }

    private suspend fun clearCredentials() {
        runLogged("Clearing credential state") {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    /** Runs [block]; failures are logged and left for the next resume to retry. */
    private suspend fun runLogged(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "$what failed; will retry later", e)
        }
    }

    companion object {
        /** Must match the region the Cloud Functions are deployed to (backend/functions/src/config.ts). */
        const val REGION = "us-central1"
        private const val TAG = "Online"
        private const val OFFLINE = "You're offline. Try again in a moment"
        private const val AUTH_PORT = 9099
        private const val FIRESTORE_PORT = 8080
        private const val FUNCTIONS_PORT = 5001
        private const val AVATAR_DEBOUNCE_MS = 1_200L
        private const val SUBMIT_GAP_MS = 5_500L
        private const val BOARD_CACHE_DIR = "boards"
        private val emulatorsWired = AtomicBoolean(false)
    }
}
