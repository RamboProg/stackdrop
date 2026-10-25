package com.stackdrop.game.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.stackdrop.game.ads.InterstitialPacing
import com.stackdrop.game.audio.Haptics
import com.stackdrop.game.audio.SoundManager
import com.stackdrop.game.data.PendingRun
import com.stackdrop.game.data.PendingRuns
import com.stackdrop.game.data.SaveData
import com.stackdrop.game.data.SaveMerge
import com.stackdrop.game.data.SaveSnapshot
import com.stackdrop.game.game.tower.TowerMode
import com.stackdrop.game.online.AccountSnapshot
import com.stackdrop.game.online.BoardEntry
import com.stackdrop.game.online.BoardSnapshot
import com.stackdrop.game.online.Boards
import com.stackdrop.game.online.Friend
import com.stackdrop.game.online.FriendCodes
import com.stackdrop.game.online.FriendRequest
import com.stackdrop.game.online.FriendsRepo
import com.stackdrop.game.online.FriendsSnapshot
import com.stackdrop.game.online.NameRules
import com.stackdrop.game.online.OnlineApi
import com.stackdrop.game.online.ProfileSnapshot
import com.stackdrop.game.platform.ConfirmPrompt
import com.stackdrop.game.platform.PlatformBridge
import com.stackdrop.game.platform.RewardPlacement
import com.stackdrop.game.platform.TextPrompt
import com.stackdrop.game.ui.Avatars
import com.stackdrop.game.ui.Hud
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.StudioSplash
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

class Game(private val context: Context, val bridge: PlatformBridge, private val online: OnlineApi) {

    enum class State { MENU, THEMES, PLAYING, GAME_OVER, PROFILE, LEADERBOARD, FRIENDS, SETTINGS }

    val save = SaveData(context)
    private val sound = SoundManager(context)
    private val haptics = Haptics(context)
    private val neon = Neon(loadFont())
    private val backdrop = Backdrop()
    val hud = Hud(this, neon)
    private val confetti = Particles()
    private val host = ModeHost(sound, haptics, backdrop, neon, hud)

    val modes: List<GameMode>
    var mode: GameMode
        private set

    var state = State.MENU
        private set
    var stateTime = 0f
        private set
    var time = 0f
        private set

    /** The theme on screen. While previewing on the Themes screen it differs from the saved one. */
    var theme: Theme = Palette.byId(Palette.NEW_PLAYER_ID)
        private set
    val previewing get() = theme.id != save.selectedTheme

    /** Today's free-to-use locked theme, refreshed when the Themes screen opens. */
    var freeToday: Theme? = null
        private set
    var reduceMotion = false
        private set
    var account = AccountSnapshot.SIGNED_OUT
        private set

    /** Public profile; null until the player chooses a name. */
    var profile: ProfileSnapshot? = null
        private set

    /** A name change is waiting for the server. */
    var nameBusy = false
        private set
    val onlineAvailable get() = online.available

    /** The profile's avatar, or the one a guest picked, or a stable default from the uid. */
    val avatar: Int
        get() = profile?.avatar ?: guestAvatar.takeIf { it >= 0 } ?: Avatars.defaultFor(account.uid)
    private var guestAvatar = -1

    var gemsEarned = 0
        private set
    var newBest = false
        private set

    private val pacing = InterstitialPacing(System::currentTimeMillis)

    /** Owns the premium purchase: no banner, no interstitials, gold app icon. */
    var premium = false
        private set

    /** Localized price from Google Play, or null until Play answers. */
    var premiumPrice: String? = null
        private set
    private var adShowing = false
    private var adClosedAt = -10f
    private var earned: RewardPlacement? = null
    private var pendingStart: GameMode? = null
    private var reviveLeft = -1f
    private var reviveUsed = false
    var gemsDoubled = false
        private set

    /** e.g. "#12 this week", once the server has ranked the run that just ended. */
    var rankLine: String? = null
        private set

    /** e.g. "You passed Ali (41)": at most one per run. */
    var passedLine: String? = null
        private set
    private var lastRunId: String? = null

    enum class BoardTab { WEEK, ALL_TIME, FRIENDS }

    /** Leaderboard screen selection, and the latest copy of every board seen this session. */
    var boardMode = "stack"
        private set
    var boardTab = BoardTab.WEEK
        private set
    private val boards = HashMap<String, BoardSnapshot>()

    /** The leaderboard row the player tapped, and that player's profile once it loads. */
    var popover: BoardEntry? = null
        private set
    var popoverProfile: ProfileSnapshot? = null
        private set

    var friends: FriendsSnapshot? = null
        private set
    val hasFriendRequests get() = friends?.incoming?.isNotEmpty() == true
    fun isFriend(uid: String) = friends?.friends?.any { it.uid == uid } == true
    /** Worked out when the board is requested, not per frame, since it needs a calendar. */
    var currentBoardId = ""
        private set
    val currentBoard: BoardSnapshot? get() = boards[currentBoardId]

    private var width = 0
    private var height = 0
    private var fade = 0f
    private val fadePaint = Paint()
    private val touches = TouchQueue()
    private val touchSink = TouchSink(::handleTouch)
    private val drag = DragTracker()
    private val events = ConcurrentLinkedQueue<GameEvent>()

    private val confettiColors = IntArray(5)
    private var crossFade = 0f
    private var fadeFrame: Bitmap? = null
    private var fadeCanvas: Canvas? = null
    private val fadeDst = RectF()
    private val crossPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    /** Plays once per launch over the menu; Reduce motion skips it. */
    private val splash = StudioSplash(context.assets)

    init {
        sound.muted = !save.soundOn
        sound.setLevel(save.soundLevel)
        haptics.enabled = save.vibrationOn
        applyComfort()
        if (reduceMotion) splash.finish()
        modes = listOf(StackMode(host), TowerMode(host))
        mode = modes.firstOrNull { it.id == save.lastMode } ?: modes[0]
        refreshFreeToday()
        applyTheme(usableSavedTheme())
        guestAvatar = save.avatar
        premium = save.premium
        pacing.enabled = !premium
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h
        drag.slop = w * TAP_SLOP
        backdrop.resize(w, h)
        hud.resize(w, h)
        modes.forEach { it.resize(w, h) }
    }

    /** Called on the UI thread with a [TouchQueue] action. */
    fun onTouch(action: Int, x: Float, y: Float) = touches.push(action, x, y)

    /** Safe from any thread; applied at the start of the next update. */
    fun post(event: GameEvent) {
        events.add(event)
    }

    fun onResume() = sound.resumeMusic()

    /** UI thread. Reading the save here is safe; it is only written on the game thread. */
    fun onPause() {
        sound.pauseMusic()
        if (save.dirty) online.pushNow(save.snapshot())
    }

    fun release() = sound.release()

    fun update(dt: Float) {
        while (true) {
            val event = events.poll() ?: break
            handleEvent(event)
        }
        touches.drain(touchSink)
        val glide = drag.update(dt)
        if (glide != 0f) hud.onDrag(glide)

        time += dt
        stateTime += dt
        fade = max(0f, fade - dt * 3f)
        crossFade = max(0f, crossFade - dt / CROSS_FADE_TIME)

        mode.update(dt)
        // When a run is lost the game holds before game over, offering a second chance if one fits.
        if (state == State.PLAYING && mode.isFinished && !adShowing) {
            when {
                reviveOffered -> {
                    reviveLeft -= dt
                    if (reviveLeft <= 0f) declineRevive()
                }
                canOfferRevive() -> reviveLeft = REVIVE_SECONDS
                else -> endRun()
            }
        }

        confetti.update(dt, width.toFloat())
        hud.update(dt)
        sound.update(dt)
        splash.update(dt)
    }

    private fun handleTouch(action: Int, x: Float, y: Float) {
        if (splash.active) {
            if (action == TouchQueue.DOWN) splash.skip()
            return
        }
        // A tap meant for the ad's close button must not drop a block.
        if (adShowing || time - adClosedAt < AD_TAP_GUARD) return
        when (action) {
            TouchQueue.DOWN -> {
                drag.down(x, y)
                if (!hud.tapsOnUp) hud.onTap(x, y)
            }
            TouchQueue.MOVE -> {
                val dy = drag.move(x, y)
                if (dy != 0f) hud.onDrag(dy)
            }
            TouchQueue.UP -> if (drag.up() && hud.tapsOnUp) hud.onTap(x, y)
            TouchQueue.CANCEL -> drag.cancel()
        }
    }

    private fun handleEvent(event: GameEvent) {
        when (event) {
            GameEvent.Back -> if (splash.active) splash.skip() else if (!hud.onBack()) backToMenu()
            is GameEvent.Toast -> hud.showBanner(event.message)
            is GameEvent.AuthChanged -> account = event.account
            is GameEvent.CloudLoaded -> applyCloud(event.remote)
            is GameEvent.CloudPushed -> save.markSynced(event.rev)
            is GameEvent.AuthFailed -> hud.showBanner(event.reason)
            is GameEvent.ProfileLoaded -> {
                profile = event.profile
                nameBusy = false
                flushRuns()
            }
            is GameEvent.BoardLoaded -> boards[event.board.id] = event.board
            is GameEvent.RunSubmitted -> {
                dropPending(event.runId)
                val rank = event.weeklyRank
                if (event.runId == lastRunId && event.newWeekly && rank != null) rankLine = "#$rank this week"
                if (state == State.LEADERBOARD) requestBoard()
            }
            is GameEvent.RunDropped -> dropPending(event.runId)
            is GameEvent.FriendsLoaded -> {
                friends = event.friends
                if (state == State.LEADERBOARD && boardTab == BoardTab.FRIENDS) requestBoard()
            }
            is GameEvent.PlayerLoaded -> if (event.profile.uid == popover?.uid) popoverProfile = event.profile
            is GameEvent.Confirmed -> onConfirmed(event.id)
            is GameEvent.RewardEarned -> earned = event.placement
            is GameEvent.AdClosed -> onAdClosed(event.placement)
            is GameEvent.PremiumChanged -> onPremiumChanged(event.owned)
            is GameEvent.PremiumPrice -> premiumPrice = event.price
            is GameEvent.NameRejected -> {
                nameBusy = false
                hud.showBanner(event.message)
            }
            is GameEvent.TextEntered -> when (event.id) {
                NAME_PROMPT -> submitName(event.value)
                CODE_PROMPT -> submitFriendCode(event.value)
            }
            GameEvent.AccountDeleted -> resetAfterDeletion()
            else -> Unit
        }
    }

    /** Merges the cloud save into the local one, then pushes the result if the cloud lacks it. */
    private fun applyCloud(remote: SaveSnapshot?) {
        val local = save.snapshot()
        if (remote == null) {
            online.pushNow(local)
            return
        }
        val merged = SaveMerge.merge(local, remote)
        val cloudHasIt = merged == remote
        if (merged != local || cloudHasIt) save.restore(merged, dirty = !cloudHasIt)
        if (!cloudHasIt) online.pushNow(merged)
        refreshFromSave()
    }

    // region Leaderboards

    fun openLeaderboard() {
        sound.play(SoundManager.BUTTON)
        boardMode = mode.id
        boardTab = BoardTab.WEEK
        closePopover()
        requestBoard()
        setState(State.LEADERBOARD)
        flushRuns()
    }

    fun selectBoard(modeId: String, tab: BoardTab) {
        if (modeId == boardMode && tab == boardTab) return
        boardMode = modeId
        boardTab = tab
        closePopover()
        sound.play(SoundManager.BUTTON)
        requestBoard()
    }

    fun requestBoard() {
        if (boardTab == BoardTab.FRIENDS) {
            currentBoardId = FriendsRepo.friendsBoardId(boardMode)
            online.loadFriendsBoard(boardMode, friends?.friends?.map { it.uid }.orEmpty())
            return
        }
        currentBoardId = Boards.id(boardMode, boardTab == BoardTab.WEEK, System.currentTimeMillis())
        online.loadBoard(currentBoardId)
    }

    /** Shows a small card for another player, with their bests and ADD FRIEND. */
    fun openPlayer(entry: BoardEntry) {
        if (entry.uid == account.uid) return
        popover = entry
        popoverProfile = null
        haptics.ui()
        online.loadPlayer(entry.uid)
    }

    fun closePopover() {
        popover = null
        popoverProfile = null
    }

    fun addFriendFromPopover() {
        val entry = popover ?: return
        online.sendFriendRequest(null, entry.uid)
        closePopover()
    }

    /** Sends queued runs once the player has a name; the server only ranks named players. */
    private fun flushRuns() {
        if (!online.available || profile == null) return
        online.submitRuns(save.pendingRuns)
    }

    private fun queueRun() {
        if (mode.score <= 0) return
        val run = PendingRun(mode.id, UUID.randomUUID().toString(), mode.runStats)
        lastRunId = run.runId
        save.pendingRuns = PendingRuns.add(save.pendingRuns, run)
        flushRuns()
    }

    private fun dropPending(runId: String) {
        save.pendingRuns = save.pendingRuns.filterNot { it.runId == runId }
    }

    // endregion

    // region Friends

    fun openFriends() {
        sound.play(SoundManager.BUTTON)
        online.loadFriends()
        setState(State.FRIENDS)
    }

    fun promptFriendCode() {
        bridge.promptText(
            TextPrompt(
                id = CODE_PROMPT,
                title = "Add a friend",
                hint = "Their code, like K7Q2-M9XD",
                initial = "",
                maxLength = 9,
                allowed = FriendCodes.ALLOWED_CHAR,
            )
        )
    }

    private fun submitFriendCode(raw: String) {
        val code = FriendCodes.normalize(raw)
        if (code == null) {
            hud.showBanner("Codes look like K7Q2-M9XD")
            return
        }
        hud.showBanner("Sending…")
        online.sendFriendRequest(code, null)
    }

    fun respondToRequest(request: FriendRequest, accept: Boolean) {
        haptics.ui()
        online.respondFriendRequest(request.fromUid, accept)
        // Hide it right away; the reload that follows confirms the change.
        friends = friends?.let { f -> f.copy(incoming = f.incoming - request) }
    }

    fun confirmRemoveFriend(friend: Friend) {
        bridge.confirm(
            ConfirmPrompt(
                id = REMOVE_PREFIX + friend.uid,
                title = "Remove ${friend.name}?",
                message = "You will stop seeing each other on the friends board.",
                confirmLabel = "Remove",
            )
        )
    }

    private fun onConfirmed(id: String) {
        if (!id.startsWith(REMOVE_PREFIX)) return
        val uid = id.removePrefix(REMOVE_PREFIX)
        online.removeFriend(uid)
        friends = friends?.let { f -> f.copy(friends = f.friends.filterNot { it.uid == uid }) }
    }

    /** The friend this run overtook, if any: the highest best between the old best and the new score. */
    private fun passedFriend(previousBest: Int, score: Int): String? {
        val list = friends?.friends ?: return null
        val best: (Friend) -> Int = { if (mode.id == "stack") it.bestStack else it.bestTower }
        val passed = list.filter { best(it) in previousBest until score }.maxByOrNull(best) ?: return null
        return "You passed ${passed.name} (${best(passed)})"
    }

    // endregion

    // region Profile and account

    fun openProfile() {
        sound.play(SoundManager.BUTTON)
        setState(State.PROFILE)
    }

    fun editName() {
        if (nameBusy) return
        bridge.promptText(
            TextPrompt(
                id = NAME_PROMPT,
                title = if (profile == null) "Choose your name" else "Change your name",
                hint = "${NameRules.MIN}–${NameRules.MAX} letters, numbers, spaces or _",
                initial = profile?.name ?: "",
                maxLength = NameRules.MAX,
                allowed = NameRules.ALLOWED_CHAR,
            )
        )
    }

    private fun submitName(raw: String) {
        val name = raw.trim()
        if (name == profile?.name) return
        val problem = NameRules.problem(name)
        if (problem != null) {
            hud.showBanner(problem)
            return
        }
        nameBusy = true
        online.claimName(name, avatar)
    }

    fun cycleAvatar() {
        val next = (avatar + 1) % Avatars.COUNT
        val p = profile
        if (p != null && online.available) {
            profile = p.copy(avatar = next)
            online.setAvatar(next)
        } else {
            guestAvatar = next
            save.avatar = next
        }
        haptics.ui()
    }

    fun copyFriendCode() {
        profile?.let { bridge.copyToClipboard(it.displayCode) }
    }

    fun shareFriendCode() {
        profile?.let { bridge.share("Add me on Stack Drop! My code: ${it.displayCode}") }
    }

    fun signInWithGoogle() = bridge.signInWithGoogle()

    fun signOut() = bridge.signOut()

    /** Permanent; the bridge asks twice before anything is deleted. */
    fun deleteAccount() = bridge.deleteAccount()

    private fun resetAfterDeletion() {
        save.resetKeepingSettings()
        profile = null
        guestAvatar = -1
        refreshFreeToday()
        applyTheme(usableSavedTheme())
        if (state != State.MENU) setState(State.MENU, withFade = true)
        hud.showBanner("Your account was deleted")
    }

    // endregion

    /** Re-applies settings and theme after the save changed underneath the game. */
    private fun refreshFromSave() {
        val wasMuted = sound.muted
        sound.muted = !save.soundOn
        sound.setLevel(save.soundLevel)
        if (wasMuted && !sound.muted) sound.resumeMusic()
        if (!wasMuted && sound.muted) sound.pauseMusic()
        haptics.enabled = save.vibrationOn
        applyComfort()
        // Switching theme resets the modes, so it waits until the player is back on a menu.
        if (state == State.MENU || state == State.SETTINGS) expireFreeTheme()
    }

    private fun syncSoon() = online.queuePush(save.snapshot())

    /** Button press feedback: the shared scale bounce plus a light tick. */
    fun press(r: RectF) {
        hud.press(r)
        haptics.ui()
    }

    fun click() = sound.play(SoundManager.BUTTON)

    private fun setState(s: State, withFade: Boolean = false) {
        state = s
        stateTime = 0f
        if (withFade) fade = 1f
        // A press that changed screens must not also count as a tap or drag on the new one.
        drag.cancel()
        hud.onEnter()
    }

    fun openThemes() {
        sound.play(SoundManager.BUTTON)
        refreshFreeToday()
        setState(State.THEMES)
    }

    /** Leaves the Themes screen, dropping any theme that was only being previewed. */
    fun closeThemes() {
        if (previewing) applyTheme(Palette.byId(save.selectedTheme))
        backToMenu()
    }

    fun openSettings() {
        sound.play(SoundManager.BUTTON)
        setState(State.SETTINGS)
    }

    fun backToMenu() {
        sound.play(SoundManager.BUTTON)
        expireFreeTheme()
        setState(State.MENU)
    }

    fun leaveGameOver() {
        sound.play(SoundManager.BUTTON)
        expireFreeTheme()
        mode.reset(theme)
        setState(State.MENU, withFade = true)
    }

    /** Starts a run, first showing an interstitial when the pacing rules allow one. */
    fun startMode(m: GameMode) {
        sound.play(SoundManager.BUTTON)
        if (!adShowing && pacing.shouldShow(save.gamesPlayed, newBest, bridge.interstitialReady())) {
            pendingStart = m
            pacing.onFullScreenShown(interstitial = true)
            beginAd()
            bridge.maybeShowInterstitial()
            return
        }
        beginMode(m)
    }

    private fun beginMode(m: GameMode) {
        mode = m
        save.lastMode = m.id
        m.reset(theme)
        confetti.clear()
        reviveUsed = false
        reviveLeft = -1f
        gemsDoubled = false
        setState(State.PLAYING, withFade = true)
    }

    // region Ads

    val reviveOffered get() = reviveLeft >= 0f

    /** Fraction of the second-chance countdown still left, 1 to 0. */
    val reviveProgress get() = (reviveLeft / REVIVE_SECONDS).coerceIn(0f, 1f)

    val canDoubleGems get() = !gemsDoubled && !adShowing && gemsEarned >= DOUBLE_MIN_GEMS && bridge.rewardedReady()

    val canGetFreeGems get() = !adShowing && save.freeGemsLeft() > 0 && bridge.rewardedReady()

    val privacyOptionsRequired get() = bridge.privacyOptionsRequired()

    private fun canOfferRevive() =
        !reviveUsed && mode.score >= REVIVE_MIN_SCORE && mode.canRevive() && bridge.rewardedReady()

    fun acceptRevive() {
        if (!reviveOffered || adShowing) return
        reviveUsed = true
        showRewarded(RewardPlacement.SECOND_CHANCE)
    }

    fun declineRevive() {
        reviveLeft = -1f
        reviveUsed = true
        endRun()
    }

    fun watchForDoubleGems() {
        if (canDoubleGems) showRewarded(RewardPlacement.DOUBLE_GEMS)
    }

    fun watchForFreeGems() {
        if (canGetFreeGems) showRewarded(RewardPlacement.FREE_GEMS)
    }

    fun showPrivacyOptions() = bridge.showPrivacyOptions()

    fun buyPremium() {
        if (!premium) bridge.buyPremium()
    }

    private fun onPremiumChanged(owned: Boolean) {
        if (owned == premium) return
        premium = owned
        save.premium = owned
        pacing.enabled = !owned
        if (owned) {
            hud.showBanner("Premium unlocked. Thank you!")
            sound.play(SoundManager.UNLOCK)
            haptics.celebrate()
            celebrate(80)
            // Bought while previewing Gilded: keep it on.
            if (previewing && theme.premiumOnly) commitTheme(theme)
        } else if (state == State.MENU || state == State.SETTINGS) {
            // Refunded: a premium theme in use falls back to the last owned one.
            expireFreeTheme()
        }
    }

    private fun showRewarded(placement: RewardPlacement) {
        earned = null
        pacing.onFullScreenShown(interstitial = false)
        beginAd()
        bridge.showRewarded(placement)
    }

    private fun beginAd() {
        adShowing = true
        sound.suppressed = true
    }

    /** Rewards are applied here, after the ad is gone, so play resumes smoothly. */
    private fun onAdClosed(placement: RewardPlacement?) {
        adShowing = false
        sound.suppressed = false
        adClosedAt = time
        val rewarded = placement != null && earned == placement
        earned = null
        when (placement) {
            RewardPlacement.SECOND_CHANCE -> {
                reviveLeft = -1f
                if (rewarded) {
                    mode.revive()
                    rewardFeedback()
                } else {
                    endRun()
                }
            }
            RewardPlacement.DOUBLE_GEMS -> if (rewarded) {
                save.gems += gemsEarned
                gemsEarned *= 2
                gemsDoubled = true
                rewardFeedback()
                syncSoon()
            }
            RewardPlacement.FREE_GEMS -> if (rewarded) {
                save.gems += FREE_GEMS
                save.useFreeGems()
                hud.showBanner("+$FREE_GEMS gems")
                rewardFeedback()
                syncSoon()
            }
            null -> pendingStart?.let {
                pendingStart = null
                beginMode(it)
            }
        }
    }

    private fun rewardFeedback() {
        sound.play(SoundManager.UNLOCK)
        haptics.celebrate()
    }

    // endregion

    private fun endRun() {
        pacing.onRunEnded()
        setState(State.GAME_OVER)
        gemsEarned = mode.gemsEarned * (if (premium) PREMIUM_GEM_MULTIPLIER else 1)
        save.gems += gemsEarned
        save.gamesPlayed++
        val previousBest = save.best(mode.id)
        newBest = mode.score > 0 && save.submitScore(mode.id, mode.score)
        if (newBest) {
            celebrate(110)
            sound.play(SoundManager.UNLOCK)
            haptics.celebrate()
        }
        val reached = ThemeRules.newlyReached(Palette.themes, mode.id, previousBest, mode.score)
        for (t in reached) save.unlock(t.id)
        val first = reached.firstOrNull()
        if (first != null) hud.showBanner("${first.name} theme unlocked!")
        if (newBest) {
            save.newBestCount++
            if (first == null) maybeNudgeGoogle()
        }
        rankLine = null
        // Passing a friend always comes with a new best, whose celebration already played.
        passedLine = passedFriend(previousBest, mode.score)
        queueRun()
        syncSoon()
    }

    /** Once, after the third new best: a gentle hint that a guest's progress lives only on this phone. */
    private fun maybeNudgeGoogle() {
        if (!online.available || !account.anonymous || account.uid == null) return
        if (save.googleNudgeShown || save.newBestCount < NUDGE_AFTER_BESTS) return
        save.googleNudgeShown = true
        hud.showBanner("Keep your progress safe: sign in")
    }

    fun toggleVibration() {
        save.vibrationOn = !save.vibrationOn
        haptics.enabled = save.vibrationOn
        sound.play(SoundManager.BUTTON)
        if (save.vibrationOn) haptics.perfect(1)
        syncSoon()
    }

    /** Picks a volume step from the Settings bar; 0 is off. Plays a tick at the new loudness. */
    fun setSoundLevel(level: Int) {
        val wasOn = save.soundOn
        save.soundLevel = level
        sound.setLevel(save.soundLevel)
        sound.muted = !save.soundOn
        when {
            save.soundOn && !wasOn -> sound.resumeMusic()
            !save.soundOn && wasOn -> sound.pauseMusic()
        }
        sound.play(SoundManager.BUTTON)
        if (save.soundOn != wasOn) syncSoon()
    }

    /** Mutes, or unmutes back to the step the player had before. */
    fun toggleSound() = setSoundLevel(if (save.soundOn) 0 else save.lastSoundLevel)

    fun toggleReduceMotion() {
        save.reduceMotion = !save.reduceMotion
        applyComfort()
        sound.play(SoundManager.BUTTON)
        syncSoon()
    }

    fun claimDaily() {
        if (!save.canClaimDaily()) {
            hud.showBanner("Next gift tomorrow")
            sound.play(SoundManager.BUTTON, 0.8f)
            return
        }
        val reward = save.claimDaily()
        hud.showBanner("+$reward gems   Day ${save.streak} streak")
        sound.play(SoundManager.UNLOCK)
        haptics.celebrate()
        celebrate(60)
        syncSoon()
    }

    /**
     * A usable theme is put in use. A locked one is previewed on the first tap and bought on the
     * second, if the player has the gems.
     */
    fun selectTheme(t: Theme) {
        when {
            canUse(t) -> {
                if (t.id == save.selectedTheme && !previewing) return
                commitTheme(t)
                sound.play(SoundManager.BUTTON)
                haptics.ui()
                if (!save.isUnlocked(t.id)) hud.showBanner("${t.name} is free today")
            }
            // Premium themes preview like any other; the second tap offers premium instead of gems.
            t.premiumOnly -> if (t.id != theme.id) {
                applyTheme(t)
                sound.play(SoundManager.BUTTON)
                haptics.ui()
                hud.showBanner("Premium theme. Tap again to get it")
            } else {
                buyPremium()
            }
            t.id != theme.id -> {
                applyTheme(t)
                sound.play(SoundManager.BUTTON)
                haptics.ui()
                hud.showBanner(if (save.gems >= t.cost) "Tap again to unlock" else gemsNeeded(t))
            }
            save.gems < t.cost -> {
                hud.showBanner(gemsNeeded(t))
                sound.play(SoundManager.BUTTON, 0.8f)
            }
            else -> {
                save.gems -= t.cost
                save.unlock(t.id)
                commitTheme(t)
                refreshFreeToday()
                sound.play(SoundManager.UNLOCK)
                haptics.celebrate()
                celebrate(70)
                hud.showBanner("${t.name} unlocked!")
            }
        }
    }

    private fun gemsNeeded(t: Theme) = "${t.cost - save.gems} more gems to unlock"

    private fun canUse(t: Theme) = save.isUnlocked(t.id) || t.id == freeToday?.id

    private fun commitTheme(t: Theme) {
        save.selectedTheme = t.id
        if (save.isUnlocked(t.id)) save.ownedTheme = t.id
        if (theme.id != t.id) applyTheme(t)
        syncSoon()
    }

    private fun applyTheme(t: Theme) {
        snapshotScene()
        theme = t
        neon.applyTheme(t)
        modes.forEach { it.reset(t) }
        sound.setAmbient(t.id)
        for (k in confettiColors.indices) confettiColors[k] = Palette.blockColor(t, k * 3)
    }

    private fun refreshFreeToday() {
        freeToday = ThemeRules.freeToday(Palette.themes, save.today()) { save.isUnlocked(it.id) }
    }

    /** The saved theme, unless it was yesterday's free theme; then the last owned one. */
    private fun usableSavedTheme(): Theme {
        val saved = Palette.byId(save.selectedTheme)
        if (canUse(saved)) return saved
        val owned = Palette.byId(save.ownedTheme)
        val fallback = if (save.isUnlocked(owned.id)) owned else Palette.byId(Palette.NEW_PLAYER_ID)
        save.selectedTheme = fallback.id
        return fallback
    }

    private fun expireFreeTheme() {
        refreshFreeToday()
        val usable = usableSavedTheme()
        if (usable.id != theme.id) applyTheme(usable)
    }

    private fun applyComfort() {
        reduceMotion = save.reduceMotion
        host.reduceMotion = reduceMotion
        backdrop.reduceMotion = reduceMotion
    }

    private fun celebrate(count: Int) {
        confetti.confetti(width.toFloat(), host.particles(theme, count), if (theme.calm) confettiColors else null)
    }

    /** Keeps a half-size copy of the current scene so a theme change can cross-fade from it. */
    private fun snapshotScene() {
        if (width == 0 || height == 0) return
        val bw = max(1, width / 2)
        val bh = max(1, height / 2)
        var bmp = fadeFrame
        if (bmp == null || bmp.width != bw || bmp.height != bh) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            fadeFrame = bmp
            fadeCanvas = Canvas(bmp)
        }
        val c = fadeCanvas ?: return
        c.save()
        c.scale(bw / width.toFloat(), bh / height.toFloat())
        mode.render(c, time)
        c.restore()
        crossFade = 1f
    }

    fun render(canvas: Canvas) {
        if (width == 0) return
        mode.render(canvas, time)
        if (crossFade > 0f) {
            fadeFrame?.let {
                crossPaint.alpha = (255 * crossFade).toInt()
                fadeDst.set(0f, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(it, null, fadeDst, crossPaint)
            }
        }
        confetti.drawScreen(canvas)
        hud.draw(canvas)
        backdrop.drawOverlay(canvas, theme)
        if (fade > 0f) {
            fadePaint.color = Color.BLACK
            fadePaint.alpha = (255 * fade * fade).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        }
        splash.render(canvas, width.toFloat(), height.toFloat())
    }

    private fun loadFont(): Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    companion object {
        const val RESTART_DELAY = 0.5f
        /** A press that travels less than this share of the screen width is a tap, not a drag. */
        private const val TAP_SLOP = 0.02f
        private const val CROSS_FADE_TIME = 0.5f
        private const val NAME_PROMPT = "name"
        private const val CODE_PROMPT = "friend_code"
        private const val REMOVE_PREFIX = "remove_friend:"
        private const val REVIVE_SECONDS = 5f
        private const val REVIVE_MIN_SCORE = 10
        private const val DOUBLE_MIN_GEMS = 3
        private const val FREE_GEMS = 25
        private const val PREMIUM_GEM_MULTIPLIER = 2
        private const val AD_TAP_GUARD = 0.3f
        private const val NUDGE_AFTER_BESTS = 3
    }
}
