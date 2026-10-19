# Stack Drop — Integration Spec for Claude Code

Accounts, Sign in with Google, leaderboards, friends, ads, and a new family of calm themes.

This document is the single source of truth for this work. Read all of it before writing code, then implement it **phase by phase** (section 12). Each phase must compile and be playable before the next one starts.

---

## 0. Working agreement (read first)

1. **Do not run terminal commands** (no Gradle, adb, npm, firebase CLI, git). The owner runs everything. When something must be run or clicked, write the exact steps for the owner, preferring Android Studio menus over commands.
2. **Never break offline play.** The game must start, play, save, and spend gems with no network and no account. Every online feature degrades to a friendly "offline" state.
3. **The game thread owns game state.** Never touch `Game`, a mode, or `Hud` fields from a network, ads, or UI callback. Post an event (section 3.3).
4. **Nothing allocates in the render loop.** Pre-render bitmaps on resize or theme change, as `Backdrop` and `Skyline` already do.
5. **Feedback must stay gentle.** The owner explicitly wants feedback that is not harsh: soft haptics, no aggressive shakes, no flashing faster than 3 times a second.
6. **Match the existing code style.** Kotlin, small focused classes, constants in `companion object`, and comments only where the code cannot speak for itself.
7. **Pin the latest stable library versions** at implementation time; check each library's release notes. The version numbers in this document are examples, not requirements.
8. **Keep the existing save keys working** so current players keep their best scores, gems, and themes. These are the `SharedPreferences` keys in `SaveData`, plus the theme ids `classic`, `pastel`, `neon`, `sunset`, `ocean`, `mono`.

---

## 1. Current codebase (what exists today)

Android, Kotlin, fully native (no engine). Package `com.stackdrop.game`. AGP 9.x with built-in Kotlin. compileSdk/targetSdk 37, minSdk 24. Portrait only. Version catalog at `gradle/libs.versions.toml`.

```
app/src/main/java/com/stackdrop/game/
  MainActivity.kt          edge-to-edge, keeps screen on, hosts GameView, forwards resume/pause/destroy
  game/GameView.kt         SurfaceView; creates Game(context); ACTION_DOWN -> game.onTap(x,y)
  game/GameLoop.kt         dedicated thread, fixed 60 UPS, lockHardwareCanvas on API 26+
  game/Game.kt             shell: State {MENU, THEMES, PLAYING, GAME_OVER}, tap queue, rewards, theme shop
  game/GameMode.kt         GameMode interface + ModeHost(sound, haptics, backdrop, neon, hud) + perfectPitch()
  game/StackMode.kt        isometric stacking mode (id "stack")
  game/tower/TowerMode.kt  crane/tower mode with lean, sway, lives (id "tower")
  game/tower/Skyline.kt    pre-rendered city silhouettes
  game/Backdrop.kt         synthwave sky, stars, striped sun, scrolling grid, scanlines, vignette
  game/IsoRenderer.kt      neon isometric blocks (dark faces + glowing edges)
  game/Palette.kt          Theme data class (HSV-driven) + 6 neon themes + color helpers
  game/Camera.kt, Particles.kt, Debris.kt, Block.kt
  ui/Neon.kt               glow drawing kit (4-pass strokes, glow text, chroma glitch text, panels, gems, hearts)
  ui/Hud.kt                draws every screen on the canvas; button RectFs; banners; combo labels
  audio/SoundManager.kt    SoundPool, loads res/raw by name, optional music
  audio/Haptics.kt         gentle haptic vocabulary: ui, land, perfect(combo), soft, warn, celebrate
  data/SaveData.kt         SharedPreferences: gems, best per mode, themes, streak/daily gift, settings
```

Important facts:
- **The UI is drawn entirely on the `SurfaceView` canvas.** There are no Android Views on screen, so text input and system dialogs must come from the Activity (section 3.2).
- **Taps arrive on the UI thread** and are queued in a `ConcurrentLinkedQueue<PointF>`, then handled in `Game.update()` on the game thread. Reuse this pattern.
- **Gameplay reacts on `ACTION_DOWN`** for responsiveness. Keep it that way while `PLAYING`.
- **Best scores are per mode.** `SaveData.best(modeId)` and `submitScore(modeId, score)`; the stack mode uses the legacy key `"best"`.
- **Gems** are earned per run (`mode.gemsEarned`) and from the daily gift (10 × streak, capped at day 7). Gems are spent on themes.

---

## 2. Feature summary

| Feature | Approach |
|---|---|
| Accounts | Firebase Authentication. Silent **anonymous** account on first launch; can be upgraded to Google. Firestore cloud save. |
| Sign in with Google | Android **Credential Manager** + `googleid` library, then a Firebase `GoogleAuthProvider` credential. The anonymous account is linked so progress is kept. |
| Leaderboards | Firestore boards per mode: **All time**, **This week**, **Friends**. Scores are written **only** by a Cloud Function that validates them. App Check (Play Integrity) is enforced. |
| Friends | Friend codes plus requests (send, accept, decline, remove), handled by Cloud Functions. Friends board, and a "you passed a friend" moment on the game-over screen. |
| Ads | Google Mobile Ads (AdMob) with **UMP consent**. Rewarded ads only by player choice; rare, capped interstitials between runs. **No banners.** |
| Calm themes | A new **Calm** theme family with matte blocks, soft gradients, slow ambient scenery, low glow, and comfortable contrast. Neon themes stay as a second family. Adds a **Reduce motion** setting. |

Backend choice: **Firebase** (Auth, Firestore, Cloud Functions v2, App Check). Google Play Games Services is **not** used, because its friends and social features are too limited for this design. It can be added later only for achievements.

---

## 3. Architecture changes (Phase 0, before any feature)

### 3.1 New packages

```
com.stackdrop.game/
  platform/PlatformBridge.kt     interface the game calls for Activity-only work
  platform/ActivityBridge.kt     implementation inside MainActivity's world (UI thread)
  game/GameEvent.kt              sealed class of results posted back to the game thread
  online/OnlineServices.kt       owns the Auth/Firestore/Functions singletons and a CoroutineScope
  online/AuthRepo.kt             anonymous + Google sign-in, link, sign out, delete
  online/ProfileRepo.kt          public profile, username claim, avatar
  online/CloudSave.kt            sync SaveData <-> users/{uid}
  online/LeaderboardRepo.kt      fetch boards, rank, submit (via function)
  online/FriendsRepo.kt          friend code lookup, requests, list
  online/OnlineState.kt          immutable snapshots the game thread reads
  ads/ConsentManager.kt          UMP flow + privacy options
  ads/AdsManager.kt              preload/show rewarded + interstitial, pacing rules
  ui/screens/                    one class per screen (see 3.4)
```

Dependencies to add, through the version catalog:
- **Firebase BoM** with `firebase-auth`, `firebase-firestore`, `firebase-functions`, `firebase-appcheck-playintegrity`, and `firebase-appcheck-debug` (debug builds only). Use the main modules, not the deprecated `-ktx` ones; the Kotlin extensions are included, for example `com.google.firebase.auth.auth`.
- **Google Services Gradle plugin** `com.google.gms.google-services`, at project level with `apply false`, then applied in `app`. Confirm compatibility with AGP 9.
- **Credential Manager:** `androidx.credentials:credentials`, `androidx.credentials:credentials-play-services-auth`, `com.google.android.libraries.identity.googleid:googleid`.
- **Ads:** `com.google.android.gms:play-services-ads`, `com.google.android.ump:user-messaging-platform`.
- **Coroutines:** `org.jetbrains.kotlinx:kotlinx-coroutines-android`, `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` (for `Task.await()`).
- **Lifecycle:** `androidx.lifecycle:lifecycle-runtime-ktx` (for `lifecycleScope`).
- Optional: `firebase-crashlytics`, `firebase-analytics` (only analytics events that respect consent).

The release build has R8 optimization enabled. Firebase and GMA ship consumer keep rules, so confirm the release build launches, signs in, and shows test ads.

### 3.2 PlatformBridge

The game must never hold an `Activity`. Instead, `Game` receives a bridge:

```kotlin
interface PlatformBridge {
    fun signInWithGoogle()
    fun signOut()
    fun deleteAccount()
    fun promptText(request: TextPrompt)          // username, friend code
    fun share(text: String)                      // friend code share sheet
    fun openUrl(url: String)                     // privacy policy, account deletion page
    fun showRewarded(placement: RewardPlacement)
    fun maybeShowInterstitial()
    fun showPrivacyOptions()                     // UMP privacy options form
    fun exitToHome()                             // moveTaskToBack(true)
}

data class TextPrompt(val id: String, val title: String, val hint: String, val initial: String, val maxLength: Int)
enum class RewardPlacement { DOUBLE_GEMS, SECOND_CHANCE, FREE_GEMS }
```

- `MainActivity` constructs `GameView(this, bridge)`, and `GameView` passes the bridge to `Game`.
- Every bridge method may be called from the game thread, so the implementation must hop to the UI thread (`activity.runOnUiThread` or `lifecycleScope.launch(Dispatchers.Main)`).
- Text prompts use a Material `AlertDialog` with an `EditText` (Material is already a dependency). Apply length and character filters, and show the keyboard immediately. The result is posted as a `GameEvent.TextEntered(id, value)` or `GameEvent.TextCancelled(id)`.

### 3.3 Events back to the game thread

```kotlin
sealed class GameEvent {
    data class TextEntered(val id: String, val value: String) : GameEvent()
    data class TextCancelled(val id: String) : GameEvent()
    data class AuthChanged(val account: AccountSnapshot) : GameEvent()
    data class AuthFailed(val reason: String) : GameEvent()
    data class BoardLoaded(val board: BoardSnapshot) : GameEvent()
    data class FriendsLoaded(val friends: FriendsSnapshot) : GameEvent()
    data class Toast(val message: String) : GameEvent()
    data class RewardEarned(val placement: RewardPlacement) : GameEvent()
    data class AdClosed(val placement: RewardPlacement?) : GameEvent()
    data class RankPassed(val friendName: String, val friendScore: Int) : GameEvent()
    object Back : GameEvent()
}
```

`Game` gets `private val events = ConcurrentLinkedQueue<GameEvent>()` and `fun post(e: GameEvent)`. Drain it at the top of `update()`, next to the tap queue. Snapshots are immutable data classes, so they are safe to pass between threads. `OnlineState` may also keep `@Volatile` latest snapshots for screens to read.

### 3.4 Split `Hud` into screens

`Hud.kt` already holds four screens and would hold nine after this work. Split it:

```kotlin
interface Screen {
    fun resize(w: Float, h: Float, u: Float)
    fun update(dt: Float) {}
    fun draw(canvas: Canvas)
    fun onTap(x: Float, y: Float): Boolean
    fun onDrag(dy: Float) {}            // list scrolling
    fun onBack(): Boolean               // true = handled
}
```

- Screens: `MenuScreen`, `PlayingScreen`, `GameOverScreen`, `ThemesScreen`, `ProfileScreen`, `LeaderboardScreen`, `FriendsScreen`, `SettingsScreen`.
- Shared widgets move to `ui/Widgets.kt`: `pill`, `pressScale`, `tabs`, `listRow`, `avatar`, `spinner`, `emptyState`, and banners.
- `Game.State` gains `PROFILE`, `LEADERBOARD`, `FRIENDS`, and `SETTINGS`.
- Banners, combo labels, and `press()` feedback stay global. Keep the visual output of the existing screens identical in this phase.

### 3.5 Input: drags and back

- `GameView.onTouchEvent` must queue `DOWN`, `MOVE`, and `UP` events with coordinates.
  - While `PLAYING`, `DOWN` triggers the gameplay tap exactly as today.
  - On list screens, a tap fires on `UP` when total travel is under 2% of screen width; otherwise movement scrolls with light inertia (friction around 4/s).
  - Menu buttons keep firing on `DOWN`, as today.
- Back: `MainActivity` registers `onBackPressedDispatcher.addCallback`, which posts `GameEvent.Back`.
  - Sub-screens go back to MENU.
  - PLAYING ignores back (no accidental quits).
  - GAME_OVER goes to MENU.
  - MENU calls `bridge.exitToHome()`.

---

## 4. Firebase setup (owner steps; Claude Code writes these instructions out, it does not do them)

1. In the [Firebase console](https://console.firebase.google.com), create the project **Stack Drop** and add an Android app with package `com.stackdrop.game`.
2. Get the SHA-1 and SHA-256 fingerprints. In Android Studio: **Gradle tool window → app → Tasks → android → signingReport**, double-click it, and read the output in the Run panel. Add the **debug** and **release** keystores. After the app goes live on Play, also add the **Play App Signing** key from Play Console → App integrity.
3. Download `google-services.json` into `app/`. Keep it out of public repositories if the repo is ever made public; it is not secret, but it is tidier that way.
4. Under **Authentication → Sign-in method**, enable **Anonymous** and **Google**. Set the support email.
5. Create a **Firestore** database in production mode, choosing the region nearest to the players. Use the same region for Functions.
6. Upgrade to the **Blaze** plan; Cloud Functions require it. Set a budget alert (for example, $5/month).
7. Under **App Check**, register the Android app with **Play Integrity**. Register the debug token printed in Logcat by `DebugAppCheckProviderFactory` for test devices. Enforce App Check for Firestore and Functions **only after** confirming it works.
8. The `default_web_client_id` string is generated from `google-services.json` and used as the `serverClientId` for Google sign-in.

Claude Code writes the backend in `/backend` (sibling of `app/`):

```
backend/
  firebase.json  .firebaserc  firestore.rules  firestore.indexes.json
  functions/  package.json  tsconfig.json  src/index.ts  src/validate.ts  src/names.ts  src/week.ts
```

Then it tells the owner how to deploy (`firebase deploy --only functions,firestore`) and how to test locally with the **Firebase Emulator Suite**. In debug builds, the Android app connects to the emulators when `BuildConfig.USE_EMULATORS` is true.

---

## 5. Accounts

### 5.1 Lifecycle

1. **First launch.** `AuthRepo.ensureSignedIn()` calls `signInAnonymously()` silently in the background. If it fails (offline), retry on the next app resume. The game never waits for it.
2. **Profile creation is optional.** The player creates a profile when they first open Ranks or Friends, or after their first run with a new best ("Put your name on the board?" pill on the game-over screen).
   - A profile is a **display name** plus an **avatar**.
   - Anonymous users may create a profile, which keeps the start frictionless.
3. **Upgrading to Google** protects progress across devices. A gentle nudge appears on the Profile screen and once after the player's 3rd best score: "Keep your progress safe — Sign in with Google". Never show it on a modal on launch.

### 5.2 Sign in with Google (Credential Manager)

```kotlin
suspend fun signInWithGoogle(activity: Activity): AccountSnapshot {
    val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = CredentialManager.create(activity).getCredential(activity, request)
    val cred = result.credential
    require(cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
    val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
    val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)

    val current = auth.currentUser
    return if (current != null && current.isAnonymous) {
        try {
            current.linkWithCredential(firebaseCred).await()      // same uid, nothing to migrate
        } catch (e: FirebaseAuthUserCollisionException) {
            val anonData = cloudSave.readLocalSnapshot()
            auth.signInWithCredential(firebaseCred).await()       // switch to the existing Google account
            cloudSave.mergeIntoCurrent(anonData)                 // see 5.4
        }
        snapshot()
    } else {
        auth.signInWithCredential(firebaseCred).await(); snapshot()
    }
}
```

- Handle `GetCredentialCancellationException` silently (the user backed out). For `NoCredentialException`, show the banner "No Google account on this device". For any other failure, show "Couldn't sign in. Try again".
- **Sign out:** `auth.signOut()`, then `CredentialManager.clearCredentialState(ClearCredentialStateRequest())`, then sign in anonymously again. Local progress stays on the device, but it no longer syncs to that Google account. Ask for confirmation first: "Sign out? Progress on this phone stays here."

### 5.3 Data model

```
profiles/{uid}                 PUBLIC, written only by functions
  name: string                 "Mo_92", 3–16 chars
  nameLower: string
  avatar: int                  index into the preset avatar list
  friendCode: string           8 chars, Crockford base32 without I L O U, e.g. "K7Q2-M9XD" (dash display only)
  bests: { stack: int, tower: int }
  createdAt: timestamp

usernames/{nameLower}          { uid }               uniqueness index, functions only
friendCodes/{code}             { uid }               lookup index, functions only

users/{uid}                    PRIVATE cloud save, owner read/write (rules restrict fields)
  rev: int                     increments on every local change
  gems: int
  unlockedThemes: string[]
  selectedTheme: string
  bests: { stack: int, tower: int }
  streak: int
  lastDaily: int               local day number, same as SaveData
  gamesPlayed: int
  settings: { sound: bool, vibration: bool, reduceMotion: bool }
  updatedAt: timestamp

users/{uid}/friends/{friendUid}  { since: timestamp }            functions only
friendRequests/{fromUid_toUid}   { from, to, fromName, createdAt }  functions only

leaderboards/{board}/entries/{uid}
  board = "{mode}_all" | "{mode}_{isoWeek}"   e.g. "tower_all", "stack_2026-W42"
  score: int, name: string, avatar: int, updatedAt: timestamp

private/{uid}                  functions only: lastSubmitAt, recentRunIds[], flags
```

### 5.4 Cloud save sync rules (`CloudSave`)

`SaveData` stays the local source of truth. Cloud sync is best-effort.

- **When to sync:**
  - On sign-in or app start: pull, merge, push.
  - After each run end, theme purchase, or daily claim: push, debounced by 3 s.
  - On app pause: push if dirty.
- **Merge, deterministic and with no prompts:**
  - `bests` take the max per mode; `unlockedThemes` take the union; `gamesPlayed` takes the max.
  - `gems`, `streak`, `lastDaily`, `selectedTheme`, and `settings` come from **whichever side has the higher `rev`**. If both are equal, keep local.
  - Never add gems from both sides; that would let players duplicate gems.
- Add `rev` and a `dirty` flag to `SaveData`. Every setter increments `rev` and sets `dirty`.
- A player's best score shown in the menu stays the local one; it is already merged.

### 5.5 Username and avatar

- **Names:** 3–16 characters from `[A-Za-z0-9_ ]`, trimmed, no double spaces. The function `claimName(name)`:
  - checks a small banned-words list in `names.ts`, matched after leetspeak normalization;
  - uniqueness is enforced in a transaction on `usernames/{nameLower}`; the old name is released;
  - rate limited to 3 changes per day.
  - On a taken name, the function returns suggestions such as `Mo_92` → `Mo_921`, `Mo_92x`.
- **Avatars:** 12 presets drawn procedurally on the canvas, with no image downloads. Each is a tiny isometric block or crane glyph on a soft circle, in calm palette colors. The default is chosen by hashing the uid. **Do not** show Google profile photos; that keeps the privacy surface small and the look consistent.

### 5.6 Profile screen

- **Layout:** avatar (tap to cycle), name (tap to edit, which opens `promptText`), friend code with **COPY** and **SHARE** pills, and best score per mode.
- **Account status:**
  - Anonymous: "Guest account — progress is only on this phone", with a primary pill **SIGN IN WITH GOOGLE**. Follow Google's branding guidance: the "G" mark and the text "Sign in with Google".
  - Google: shows the account email in small, dim text, and a **SIGN OUT** pill.
- **Delete account (required by Google Play):**
  - The pill sits at the bottom in red but dim.
  - It asks for confirmation twice, then calls the `deleteAccount` function, which removes profile, username, friend code, friends (both sides), requests, leaderboard entries, `users/{uid}`, `private/{uid}`, and finally the Auth user.
  - Local data is then reset to a fresh anonymous state, keeping only settings.
  - Also provide a web URL describing how to request deletion; Play Console requires it. The owner hosts that page.

---

## 6. Leaderboards

### 6.1 What a run reports

Extend `GameMode` with run stats, collected by each mode:

```kotlin
data class RunStats(val score: Int, val durationMs: Long, val drops: Int, val perfects: Int, val bestCombo: Int, val revived: Boolean)
val runStats: RunStats
```

At the end of a run, `Game.endRun()`:
1. saves locally (unchanged);
2. if online and a profile exists, calls `LeaderboardRepo.submit(mode.id, runStats, runId = UUID)`;
3. if offline, stores the run in a **pending queue** (at most 5, keeping the highest per mode) in `SaveData` and flushes it on the next connection.

### 6.2 `submitScore` Cloud Function (callable, `enforceAppCheck: true`)

The function's checks, in order:
1. The caller is authenticated and has a `profiles/{uid}`; otherwise return `failed-precondition: "profile-required"`.
2. `mode ∈ {stack, tower}`, `0 ≤ score ≤ 5000`, and `score ≤ drops`.
3. Plausibility: `durationMs ≥ drops × MIN_DROP_MS[mode]` (stack 220 ms, tower 650 ms, tuned from real play), and `perfects ≤ drops`.
4. Rate limit: at least 5 s since `private/{uid}.lastSubmitAt`, and `runId` not among the last 20.
5. In **one transaction, all reads before writes**:
   - read `{mode}_all` and `{mode}_{week}` entries, and `private/{uid}`;
   - write each entry only if the new score is higher;
   - update `profiles/{uid}.bests.{mode}` and `private/{uid}`.
6. Return `{ newAllTime: bool, newWeekly: bool, weeklyRank?: int }`.
7. Suspicious but not impossible runs are stored with `flags++` instead of rejected, so they can be reviewed later.

`week.ts` computes the ISO week in **UTC** from server time (`2026-W42`). The client computes the same id to know which board to read. Old weekly boards stay in place; an optional scheduled function can prune boards older than 8 weeks.

### 6.3 Reading boards

- **Top list:** `entries.orderBy("score", DESC).limit(50)`.
- **My rank:** `entries.whereGreaterThan("score", myScore).count()` on the server, plus 1. This costs one read per 1,000 entries, which is cheap.
- **Friends board:** read the friend uids from `users/{uid}/friends`, add self, query `whereIn(FieldPath.documentId(), chunk)` in chunks of 30 against the board, then sort on the client.
- **Caching:** keep each board in memory for 60 s and on disk (a small JSON file in `filesDir`) to show instantly, marked "updated 5 min ago" when stale.
- Ties: equal scores show the same rank. The earlier `updatedAt` is listed first.

### 6.4 Leaderboard screen (canvas)

- **Layout:**
  - Top: mode toggle **STACK | TOWER**.
  - Below it, tabs **WEEK | ALL TIME | FRIENDS**. Default to **WEEK**: weekly boards give everyone a fresh chance, which keeps players coming back.
  - The week tab shows "Resets in 3d 4h".
  - A scrollable list of rows: rank, avatar, name, score. The top 3 get a soft gold, silver, or bronze tint (no glow).
  - The **player's own row is pinned** at the bottom of the screen when it is off-screen: "You · #128 · 34".
- **States:**
  - Loading: a soft spinner.
  - Offline: "You're offline — showing saved ranks".
  - No profile: a "Choose a name to join" pill.
  - Empty friends list: "Add friends to compete" pill, which opens Friends.
- Tapping a row opens a small popover showing that player's bests, with **ADD FRIEND** if they are not a friend yet.

### 6.5 Game-over additions

- **New weekly best:** a calm line under the score, "#12 this week".
- **"You passed X":** using the cached friends board, if the new score beats a friend's best, show a single soft line "You passed Ali (41)" with a gentle `haptics.celebrate()`. Show at most one per run. This is a strong reason to keep playing.
- If the player has no profile and this was a new best, show a secondary pill **PUT MY NAME UP**.

---

## 7. Friends

### 7.1 Flows

- **Add by code:**
  - The **ADD** pill opens `promptText` for the code; it is case-insensitive and the dash is optional.
  - The `sendFriendRequest(code)` function looks up `friendCodes`. It rejects self-requests and players who are already friends, and caps at 100 friends and 20 outgoing pending requests.
  - If the target has already requested the sender, the function auto-accepts.
- **Share code:** `bridge.share("Add me on Stack Drop! My code: K7Q2-M9XD")`. Deep links are deliberately out of scope; Firebase Dynamic Links is shut down. Android App Links on a domain the owner controls can be added later.
- **Requests:** `respondFriendRequest(fromUid, accept)` writes both `users/A/friends/B` and `users/B/friends/A` in a batch and deletes the request.
- **Remove:** `removeFriend(uid)` deletes both sides. Ask for confirmation.
- **From a leaderboard row:** **ADD FRIEND** sends a request by uid (the same function, with a `uid` parameter).

### 7.2 Friends screen (canvas)

- **Header:** "FRIENDS 7/100", with an **ADD** pill and a **MY CODE** pill that shows the code and offers share.
- **Requests section (only if any):** rows with ✓ and ✕ buttons.
- **List:** avatar, name, best stack and tower scores, sorted by best tower score. Tapping a row offers **REMOVE**.
- A soft badge dot on the menu's FRIENDS pill when there are pending incoming requests. Check on app start and resume. No push notifications in this scope.

---

## 8. Ads (AdMob)

### 8.1 Principles

The owner wants a game that feels kind. Ads must never interrupt gameplay, never appear on launch, and never punish a player.

- **No banners.** They hurt the calm look and are easy to mis-tap on a full-screen canvas.
- **Rewarded ads are always opt-in** and clearly labelled with a ▶ icon and the reward.
- **Interstitials are rare and only between runs** (pacing rules below).
- During development, **use Google's test ad unit IDs only**. Real IDs go in `BuildConfig` fields for release only.
  - Test App ID: `ca-app-pub-3940256099942544~3347511713`
  - Test Rewarded: `ca-app-pub-3940256099942544/5224354917`
  - Test Interstitial: `ca-app-pub-3940256099942544/1033173712`
- The manifest gets `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobAppId}"/>` through `manifestPlaceholders`.

### 8.2 Consent (UMP), before any ad request

On launch, `ConsentManager.gather(activity)` runs:
1. `requestConsentInfoUpdate`;
2. `loadAndShowConsentFormIfRequired`;
3. if `canRequestAds()`, initialize `MobileAds` on a background thread, then preload.

The SETTINGS screen shows **PRIVACY OPTIONS** only when `privacyOptionsRequirementStatus == REQUIRED`; it opens `showPrivacyOptionsForm`. The owner creates the GDPR message in **AdMob → Privacy & messaging**.

### 8.3 Placements

| Placement | Where | Reward | Limits |
|---|---|---|---|
| `DOUBLE_GEMS` | Game-over pill "▶ 2× GEMS" | gems earned this run are doubled | only if `gemsEarned ≥ 3`; once per run |
| `SECOND_CHANCE` | Overlay when a run ends: "Keep going? ▶" with a 5 s soft ring countdown and a "No thanks" pill | Tower: +1 life (on the last slip), or remove the top 2 floors and rebalance (on a collapse). Stack: the slab is restored to the previous width and the run resumes. | once per run; not before score 10; `runStats.revived = true` |
| `FREE_GEMS` | Themes screen pill "▶ +25" | +25 gems | 5 per day |

- **Reward timing:** grant the reward on `onUserEarnedReward`, but apply it **after** the ad is dismissed (`GameEvent.AdClosed`), so the game resumes smoothly. Then play `SoundManager.UNLOCK` and `haptics.celebrate()`.
- **Second chance** needs `fun canRevive(): Boolean` and `fun revive()` on `GameMode`.
  - Modes enter a "pending end" state: `isOver` is true, but `isFinished` waits until `Game` decides.
  - `Game` shows the overlay instead of the game-over screen when `canRevive()` and an ad is loaded.
  - In the tower, revive resets `collapsing`, `collapseAngle`, `hook = Hook.LOWERING`, and spawns a new module.
  - Revived runs still count on leaderboards; `revived` is stored but not penalized.

### 8.4 Interstitial pacing (`AdsManager.maybeShowInterstitial`)

Show one only when **all** of these are true:
- the player is going from GAME_OVER to a new run (RETRY or a mode card), never mid-game and never on MENU entry;
- at least **4 runs** since the last interstitial **and** at least **4 minutes** since the last full-screen ad of any type;
- at least 8 runs have been played in total, and the session has lasted at least 3 minutes;
- the run that just ended was **not** a new best, and the player did **not** watch a rewarded ad in the last 4 minutes;
- the ad is loaded; never wait for it.

Make these constants remote-tunable later (Firebase Remote Config is optional). Preload the next ad after each show. Discard and reload ads older than 55 minutes.

### 8.5 Behavior around full-screen ads

The ad activity pauses `MainActivity`, so `onPauseGame()` already pauses music. Additionally:
- mute SoundPool effects while an ad is showing;
- ignore taps that arrive within 300 ms of the ad closing, so a tap meant for the ad's close button doesn't trigger a drop.

### 8.6 Optional, later: Remove Ads

A one-time purchase with Play Billing, "Remove ads". It removes interstitials only; rewarded ads stay available as an opt-in. Design `AdsManager.interstitialsEnabled` now so this is a small change later.

---

## 9. Calm themes (polished, relaxing, easy on the eyes)

The owner likes the neon look, so **keep all six neon themes** exactly as they are, under a **NEON** tab. Add a **CALM** family and make **Dusk** the default for **new** installs. Existing players keep their selected theme.

### 9.1 Eye-comfort rules for every Calm theme

1. **No pure black (#000) or pure white (#FFF)** anywhere. Darkest is about L 8%; lightest about L 96%.
2. **Block saturation 0.22–0.45, value 0.72–0.92.** Hue walks slowly within an analogous band (±25° oscillation), never a full rainbow.
3. **Neighbouring layers differ in lightness by 6–9%.** That is enough to read edges while placing, without strobing. The **moving** block gets a 1-px lighter top edge (lighten 0.25, alpha 140) so it stands out without glowing.
4. **Glow at most 0.25** of the neon strength. Panels are "frosted": a fill of the panel color at alpha 170, a 1-px hairline border at alpha 60, and a larger corner radius.
5. **Ambient motion below 0.5 Hz**, at most 40 ambient particles, always behind the tower. Nothing flashes, and nothing exceeds 3 brightness changes per second.
6. **Text contrast of at least 4.5:1** against its panel or sky. Use theme tokens `text` and `textDim` instead of hard-coded `Color.WHITE`; light themes use dark text.
7. **Feedback scaled down:**
   - camera shake ×0.5;
   - particle count ×0.6;
   - the perfect hit is a single soft expanding ring plus a few slow motes, instead of flares;
   - the damage flash uses the theme's `danger` color at alpha 18;
   - the confetti palette comes from the theme's block colors.
8. **No scanlines.** Vignette strength is 0.35 (neon is 1.0). The chromatic glitch title becomes a soft glow title.

### 9.2 Theme model

Replace the HSV-only `Theme` with explicit tokens, keeping `Palette`'s function names working:

```kotlin
enum class Family { CALM, NEON }
enum class Scenery { SYNTH_GRID, HILLS, FOREST_MIST, LAGOON, AURORA_PEAKS, PETALS_GARDEN, DUNES, SLATE }
enum class Ambient { STARS, NONE, FOG, BUBBLES, AURORA, PETALS, MOTES }

data class Theme(
    val id: String, val name: String, val cost: Int, val family: Family,
    // blocks
    val baseHue: Float, val hueStep: Float, val hueRange: Float, val sat: Float, val value: Float, val valueWave: Float,
    // scene
    val skyTop: Int, val skyMid: Int, val skyBottom: Int, val ground: Int,
    val scenery: Scenery, val ambient: Ambient,
    // ui tokens
    val accent: Int, val accent2: Int, val text: Int, val textDim: Int, val panel: Int, val danger: Int,
    // intensity
    val glow: Float,          // 1 = current neon, 0.15–0.25 for calm
    val scanlines: Boolean, val vignette: Float, val shake: Float, val particles: Float,
    val light: Boolean,       // light sky -> dark text, softer shadows
)
```

- Convert the six neon themes into this shape so they render **pixel-identically** to today: `glow = 1`, `scanlines = true`, `vignette = 1`, `shake = 1`, `particles = 1`, sky colors computed from their current `bgHue`/`bgSat` values, `scenery = SYNTH_GRID`, `ambient = STARS`.
- `SaveData.isUnlocked(id)` becomes `cost == 0 || id in unlocked()`.
- `Palette.DEFAULT_ID` stays `"classic"` (for fallback), and a new `Palette.NEW_PLAYER_ID = "dusk"` is used when no theme is saved yet.

### 9.3 The Calm themes

| id | Name | Cost | Mood | Sky (top → mid → bottom) | Blocks | Accent / text | Scenery + ambient |
|---|---|---|---|---|---|---|---|
| `dusk` | Dusk | 0 | warm evening | `#2E2F4F` → `#6B5B7B` → `#D9A3A0` | hue 345→15, s .32, v .88 | `#F2C9A6` / `#F6EFE8` | 3 layered soft hills, a large faint sun disc with no stripes, a few slow stars at the top |
| `mist` | Mist | 60 | quiet morning (light) | `#E4ECE8` → `#C9D8D2` → `#A9BFB8` | hue 140–175, s .25, v .80 | `#6E8F86` / `#2F3B38` | pine silhouettes; 2 fog bands drifting at 0.02 screen/s |
| `lagoon` | Lagoon | 120 | deep calm water | `#0F2A3A` → `#174456` → `#1F5C66` | hue 170–200, s .38, v .82 | `#8FD3C8` / `#E8F4F2` | soft light rays from the top (alpha 20, swaying 0.1 Hz); bubbles rising slowly |
| `aurora` | Aurora | 200 | arctic night | `#0B1026` → `#16224A` → `#24365E` | hue 190–250, s .30, v .86 | `#9FE3C9` / `#EEF2FA` | snowy peaks; 2 aurora ribbons (alpha 35, phase drift 0.05 Hz); slow stars |
| `sakura` | Sakura | 300 | spring garden (light) | `#FBEDEA` → `#F7DCDD` → `#EEC7CC` | hue 345–20, s .26, v .94 | `#D98C9C` / `#4A3A40` | rounded tree canopies; up to 24 falling petals that sway and rotate |
| `dune` | Dune | 450 | warm afternoon (light) | `#F6E7CF` → `#EED3AE` → `#E2B98F` | hue 15–40, s .40, v .86 | `#C7774F` / `#4A3528` | layered dune curves; a faint heat-free haze; no particles |
| `slate` | Slate | 600 | minimal night (OLED-friendly) | `#15171B` → `#1A1D23` → `#1F232A` | hue 210–230, s .14, v .78 | `#A9B8CC` / `#E6E9EE` | plain, with a single thin horizon line; a few slow motes |

These hex values are a starting point. Claude Code should tune them on a real device for contrast (rule 6) and keep the "feel" column intact.

### 9.4 Rendering changes

- **`Neon`** gets `var glow = 1f` and `var textColor`, set by `Game` whenever the theme changes.
  - `line`, `path`, and `roundRect`: when `glow < 0.5`, draw one soft pass (2× width, alpha `0.10 × glow × 4`) and the crisp stroke. Skip the bright core.
  - `glowText`: scale the shadow radius and alpha by `glow`.
  - `chroma`: when `glow < 0.5`, fall back to `glowText` with no RGB split.
  - `panel`: uses `theme.panel` with the frosted style.
- **`IsoRenderer`** gets a calm path:
  - matte filled faces: top 1.0, left 0.84, right 0.70 of the block color;
  - a hairline top edge (lighten 0.2, alpha 120) instead of the glowing perimeter;
  - a soft contact shadow (alpha 26) on the block below where the new slab overhangs;
  - no vertical neon edges.
- **`Backdrop`** dispatches on `theme.scenery`.
  - Each scenery pre-renders its silhouettes to bitmaps on resize or theme change, and draws them with parallax against the camera rise: far 0.08, mid 0.18, near 0.32. Their colors are blended between `skyBottom` and `ground`.
  - `Ambient` particles live in a small fixed array, `AmbientLayer`, with no allocation per frame.
  - `drawOverlay` respects `scanlines` and `vignette`.
- **`TowerMode`** in calm themes:
  - **Facades:** matte floors in block colors. Window lights are warm `#FFE3A3` at alpha 130 on dark themes, and pale sky-tinted glass at alpha 90 on light themes.
  - **Crane:** drawn in `textDim` at 80% alpha, as thin clean lines.
  - **Skyline:** tinted from the sky colors.
  - **Balance meter:** uses `accent` and `danger`, with no amber glow.
- **Theme change:** a cross-fade over 0.5 s. Snapshot the previous frame to a reusable bitmap and draw it over the new one with decreasing alpha. This replaces the fade to black.

### 9.5 Making themes compelling without being exhausting

- **Try before you buy.** Tapping a locked theme previews it live behind the Themes screen for as long as the player stays on it, with a "PREVIEW" chip. Leaving without buying reverts.
- **Theme of the day.** One locked theme is free to *use* for the day; it rotates with the local day number. It is shown with a "FREE TODAY" chip. This gives players a reason to open the app daily, alongside the gift.
- **Milestone unlocks** as an alternative to gems: reach 25 in Tower to unlock **Aurora**; reach 40 in Stack to unlock **Lagoon**. Show "or reach 25 in Tower" on the row.
- **Optional per-theme ambient loop.** For example `res/raw/ambient_dusk.ogg`, loaded by name like other sounds and skipped if missing, at volume ≤ 0.25, cross-faded on theme change. The owner will supply the audio files.

### 9.6 Themes screen redesign

- **Tabs:** **CALM | NEON**, opening on CALM.
- **Rows:**
  - Rows are compact: height `min(h × 0.082, 15u)`, so 7 rows fit between `h × 0.2` and `h × 0.84`.
  - Each row shows a 5-swatch preview, the name, a one-word mood ("warm", "quiet", "deep"…), and a status: IN USE, OWNED, FREE TODAY, a price with the gem icon, or a milestone hint.
- **Bottom row:** **BACK**, and **▶ +25** for the free-gems rewarded ad.

### 9.7 Comfort settings

The SETTINGS screen contains:
- Sound
- Vibration
- **Reduce motion**: no camera shake, ambient particles frozen at half count, no score pop scaling, title static. Store it in `SaveData` and the cloud `settings`.
- Privacy options (only when required by UMP)
- Privacy policy (opens a URL)
- Version

The Sound and Vibration toggles move here from the main menu.

---

## 10. Menu layout after all changes

```
 [avatar · name]                         [gems ◆]  [⚙]
                 STACK
                 DROP
            ARCADE TOWERS

     ┌──────────────┐   ┌──────────────┐
     │  stack icon  │   │  crane icon  │
     │    STACK     │   │    TOWER     │
     │   BEST 41    │   │   BEST 27    │
     └──────────────┘   └──────────────┘

 [THEMES]  [RANKS]  [FRIENDS•]  [GIFT!]
```

- The avatar chip opens PROFILE. When there is no profile it shows "Guest", with a small "Sign in" dot when not on Google.
- The gear opens SETTINGS.
- Tapping empty space still starts the selected mode, as today.
- Everything respects the active theme's tokens (text color, glow, panel).

---

## 11. Security rules (`firestore.rules`)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    function signedIn() { return request.auth != null; }
    function isMe(uid) { return signedIn() && request.auth.uid == uid; }

    match /profiles/{uid}            { allow read: if signedIn(); allow write: if false; }
    match /usernames/{name}          { allow read, write: if false; }
    match /friendCodes/{code}        { allow read, write: if false; }
    match /private/{uid}             { allow read, write: if false; }

    match /users/{uid} {
      allow read: if isMe(uid);
      allow create: if isMe(uid) && request.resource.data.keys().hasOnly(
        ['rev','gems','unlockedThemes','selectedTheme','bests','streak','lastDaily','gamesPlayed','settings','updatedAt']);
      allow update: if isMe(uid) && request.resource.data.diff(resource.data).affectedKeys().hasOnly(
        ['rev','gems','unlockedThemes','selectedTheme','bests','streak','lastDaily','gamesPlayed','settings','updatedAt'])
        && request.resource.data.gems is int && request.resource.data.gems >= 0 && request.resource.data.gems <= 1000000;
      match /friends/{friendUid}     { allow read: if isMe(uid); allow write: if false; }
    }

    match /friendRequests/{id} {
      allow read: if signedIn() && (resource.data.from == request.auth.uid || resource.data.to == request.auth.uid);
      allow write: if false;
    }

    match /leaderboards/{board}/entries/{uid} { allow read: if signedIn(); allow write: if false; }
  }
}
```

- All callable functions use `onCall({ enforceAppCheck: true, region })`, check `request.auth`, and validate inputs with explicit type checks.
- Functions use the Admin SDK and bypass rules.
- Cloud-save gems can be edited by a determined cheater. That is accepted, because gems only buy cosmetics. Leaderboards are the protected surface.

---

## 12. Implementation phases and acceptance criteria

Each phase ends with written **owner test steps**: what to tap, and what should happen.

**Phase 0 — Foundations (no visible change)**
- `PlatformBridge`, `GameEvent` queue, screen split, drag input, back handling, coroutines and lifecycle dependencies.
- ✅ The game looks and plays exactly as before. Back from Themes returns to the menu. Back on the menu leaves the app.

**Phase 1 — Calm themes (no backend needed; can ship alone)**
- Theme model, the 7 Calm themes, the Neon rendering refactor, Backdrop sceneries and ambient particles, the IsoRenderer and TowerMode calm paths, the cross-fade, the new Themes screen, preview, theme of the day, milestone unlocks, the SETTINGS screen with Reduce motion.
- ✅ Neon themes are pixel-identical to before.
- ✅ Calm themes hold 60 fps on a mid-range phone (watch the Android Studio Profiler for dropped frames).
- ✅ Each Calm theme passes the comfort rules in 9.1, checked on a real phone at 50% brightness.
- ✅ Existing players keep their gems, unlocks, and selection.

**Phase 2 — Firebase + anonymous accounts + cloud save**
- Gradle and plugin setup, `OnlineServices`, `AuthRepo.ensureSignedIn`, `CloudSave` with the merge rules, an emulator toggle, App Check (debug provider in debug builds).
- ✅ Airplane mode: the game is fully playable.
- ✅ Clear the app's storage, sign in to the same account again, and progress restores.

**Phase 3 — Google sign-in + profile**
- Credential Manager flow, linking and collision merge, sign out, PROFILE screen, `claimName`, avatars, friend code generation, `deleteAccount`.
- ✅ Guest progress survives upgrading to Google.
- ✅ Signing in to the same Google account on a second device shows merged progress.
- ✅ Deleting the account removes all of its Firestore documents.

**Phase 4 — Leaderboards**
- `RunStats` in both modes, `submitScore` with validation, the pending queue, the LEADERBOARD screen (week, all time, friends tab placeholder), rank, caching, game-over rank line.
- ✅ An implausible score sent from a test build is rejected or flagged.
- ✅ A week rollover shows a fresh board.
- ✅ Offline shows the cached board.

**Phase 5 — Friends**
- Functions, FRIENDS screen, share sheet, menu badge, friends board tab, "You passed X".
- ✅ Two test accounts can add each other by code, see each other on the friends board, and remove each other.

**Phase 6 — Ads + consent**
- UMP, `AdsManager`, the three rewarded placements, revive support in both modes, interstitial pacing, test IDs, release IDs through `BuildConfig`.
- ✅ The consent form appears for an EEA test device: in debug builds use `ConsentDebugSettings` with `DebugGeography.EEA`.
- ✅ No ad ever appears during play or on launch.
- ✅ Each reward applies once, after the ad closes.

**Phase 7 — Release readiness**
- Crash-free pass, R8 release build check, privacy policy and account deletion URLs wired in Settings.
- Play Console data safety answers listed for the owner (section 13).

---

## 13. Store and policy checklist (for the owner)

- **Privacy policy URL** (required for ads and accounts). Hosting it on GitHub Pages or a Google Site is fine.
- **Data safety form:**
  - Collected: account info (name; email for Google accounts), app activity (scores, gameplay), device or other IDs (advertising ID, through AdMob).
  - Data is encrypted in transit. Users can request deletion, both in the app and through the URL.
- **Ads declaration:** Yes, the app contains ads.
- **Target audience:** 13+. Do **not** target children; that would bring in the Families policy and need different ad settings.
- **Account deletion:** an in-app path (Profile → Delete account) plus a web URL.
- **Content rating questionnaire:** user interaction exists (names visible on leaderboards).
- **AdMob:** add `app-ads.txt` on the developer website listed in Play Console.

---

## 14. Things explicitly out of scope

- Push notifications, chat, or any free-text messaging between players.
- Google profile photos or custom avatar uploads.
- Real-money purchases (except the optional, later "Remove Ads").
- Deep links or invite links (codes only, for now).
- Play Games Services achievements (possible later).
