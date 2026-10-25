# Stack Drop — what's left before launch

Everything in the code is done. What remains needs your accounts (AdMob, Play Console, Firebase)
or a real phone. Work through the parts in order; each part says how you know it worked. The
detailed reference for each topic is in `OWNER_SETUP.md` (section numbers given below).

---

## Part 1 — Upload key and first Play upload (needed by parts 2–4)

Play Billing, the Play App Signing fingerprint and the Play-installed tests all need the app to
exist in Play Console, so do this first.

1. **Create the app:** [Play Console](https://play.google.com/console) → **Create app**. Name
   "Stack Drop", type **Game**, **Free**, accept the declarations.
2. **Create your upload key:** in Android Studio, **Build → Generate Signed App Bundle or APK →
   Android App Bundle → Next → Create new...** Save the keystore *outside* the project folder
   (for example `D:\keys\stackdrop-upload.jks`), choose strong passwords, alias `upload`.
3. **Back up the keystore and passwords** (password manager plus one offline copy). If you lose
   it you can request a reset from Play, but it takes days. Never commit it.
4. Finish the wizard with the **release** variant. The bundle lands in `app/release/app-release.aab`.
5. Play Console → **Test and release → Testing → Internal testing → Create new release**. Accept
   **Play App Signing** when asked, upload the `.aab`, add release notes, **Save → Review → Start
   rollout**.
6. **Testers:** Internal testing → **Testers** tab → create a list with your own Google account.
   Copy the **opt-in link**, open it on your phone, accept, then install from the Play Store link.
7. **Add Play's signing fingerprints to Firebase:** Play Console → **Test and release → Setup →
   App signing** → copy the **SHA-1** and **SHA-256** of the *App signing key certificate*.
   Firebase console → Project settings → your Android app → **Add fingerprint** (both). Download the
   fresh `google-services.json` into `app/`. Without this, Google sign-in and App Check fail on
   Play-installed copies.

Every later upload needs a higher version code: add `stackdrop.versionCode=2` (then 3, 4...) to
`gradle.properties`. Set `stackdrop.versionName=1.0.1` whenever players should see a new number.

To let Claude build signed bundles, fill in both passwords in `keystore.properties` in the project
folder (git ignores it). Without it, release builds come out unsigned.

---

## Part 2 — Real ads (AdMob) — `OWNER_SETUP.md` §11

1. Sign in to [AdMob](https://admob.google.com) with the account that should receive the money.
2. **Apps → Add app → Android**. Say the app is **not** published yet (link it to the Play listing
   later, under App settings, once the store page is live).
3. Copy the **App ID** (`ca-app-pub-…~…`).
4. **Ad units → Add ad unit**, three times:
   - **Banner** (name it "Bottom banner").
   - **Rewarded** ("Rewarded"). Leave the reward settings at their defaults; the game decides the
     reward itself.
   - **Interstitial** ("Between runs").
   Copy each ad unit ID (`ca-app-pub-…/…`).
5. **Payments:** AdMob → **Payments → Payments info**. Fill in your address, tax information and
   bank account. Payment arrives once you pass the payment threshold (US$100 or local equivalent).
6. **Consent message:** **Privacy & messaging → European regulations → Create message**. Choose
   the app, your privacy policy URL (Part 4), languages, then **Publish**. Without a published
   message, players in the EEA, UK and Switzerland get no ads at all.
7. Put the four IDs in the project's `gradle.properties`:
   ```
   stackdrop.admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
   stackdrop.admobBannerId=ca-app-pub-XXXXXXXXXXXXXXXX/BBBBBBBBBB
   stackdrop.admobRewardedId=ca-app-pub-XXXXXXXXXXXXXXXX/RRRRRRRRRR
   stackdrop.admobInterstitialId=ca-app-pub-XXXXXXXXXXXXXXXX/IIIIIIIIII
   ```
   Only **release** builds use these. Debug builds always use Google's test ads.
   Then **delete the `stackdrop.allowTestAds=true` line** from `gradle.properties`. While it is
   there, release builds quietly use test ads; without it, a release build fails if any ID is missing.
8. **app-ads.txt:** AdMob → Apps → your app → **app-ads.txt** shows one line. Put it in a file
   called `app-ads.txt` at the root of the website you list in Play Console (for example
   `https://yoursite.example/app-ads.txt`). AdMob checks it within a day or two.
9. **Never tap your own real ads** and don't ask friends to. AdMob detects it as invalid traffic
   and can close the account. If you need to see real ads on your phone, add it as a test device:
   AdMob → **Settings → Test devices → Add**, using the advertising ID from the phone's
   Settings → Google → Ads.

**Worked when:** a release build shows ads without the "Test Ad" label (new ad units can take an
hour or two to start filling; "no fill" before then is normal).

---

## Part 3 — Premium purchase — `OWNER_SETUP.md` §12

Needs Part 1 done (Play only sells items for apps it has a build of).

1. **Payments profile:** Play Console → **Settings → Payments profile**. Link or create a merchant
   account. Without it you cannot create paid products.
2. **Create the product:** your app → **Monetize with Play → Products → One-time products →
   Create one-time product**.
   - Product ID: **`premium`** (exactly; it cannot be changed later).
   - Name: `Premium`
   - Description: `No ads, 2× gems from every run, the Gilded theme and a gold app icon`
   - Add a purchase option (buy), set the price (Play converts it to other currencies).
   - **Save**, then **Activate**.
3. **License testers:** Play Console → **Settings → License testing** → add your Google account →
   **Save**. Purchases by that account are free test orders and refund themselves.
4. On your phone, **install from Play** (the internal testing link from Part 1). A copy installed
   from Android Studio cannot buy, even with the same package name.
5. Run the seven test steps in `OWNER_SETUP.md` §12: buy, check the banner disappears, check 2×
   gems and Gilded, check the gold icon, reinstall to restore, then refund the test order in
   **Order management** to check that premium is removed.

**Worked when:** Settings → Premium shows a price instead of "Premium isn't available right now",
and the row says ACTIVE after buying.

---

## Part 4 — Before publishing

1. **Host the privacy policy and deletion page.**
   - Fill in the bracketed parts of `docs/store/privacy-policy.md` and
     `docs/store/account-deletion.md` (your name, contact email, date, the deletion URL).
   - Host both. Easiest options: a free **Google Sites** page, or a separate public GitHub
     repository with **GitHub Pages** turned on. Don't make the game's own repository public just
     for this, because it contains `app/google-services.json`.
   - Put the two addresses in `app/src/main/java/com/stackdrop/game/ui/Links.kt`
     (`PRIVACY_POLICY` and `ACCOUNT_DELETION`).
   - Use the same privacy URL in Play Console (App content → Privacy policy) and in the AdMob
     consent message.
2. **Official Google "G".** The sign-in button draws an approximation
   (`ProfileScreen.googleMark`). Download the official mark from Google's
   [Sign-in branding guidelines](https://developers.google.com/identity/branding-guidelines),
   add the PNG or SVG (as a vector drawable) to `res/drawable`, and draw it instead.
3. **App Check enforcement** — only after a Play-installed build signs in and syncs correctly:
   - Firebase console → **App Check → APIs**. For **Cloud Firestore** and **Cloud Functions**, open
     the metrics and check that almost all requests are *verified*. Then click **Enforce**.
   - **App Check → Apps → your app → ⋮ → Manage debug tokens**: delete every debug token,
     including the emulator one you registered earlier.
   - From then on, debug builds on new devices need a new debug token (`OWNER_SETUP.md` §4).
4. **Crashlytics:** Firebase console → **Crashlytics** → enable it if asked. Make sure
   `stackdrop.crashlyticsDebug` is not set in `gradle.properties` (§13).
5. **Play Console forms** (answers in `docs/PLAY_CONSOLE.md`):
   - App content: privacy policy, ads (yes), app access, target audience (13+), content rating
     questionnaire, advertising ID (yes), data safety (copy the table, including the Crashlytics
     rows).
   - Store listing: short and full description, app icon 512×512, feature graphic 1024×500, at
     least 2 phone screenshots, category Games → Casual, contact email.
6. **Production access:** new personal developer accounts must run a **closed test with at least
   12 testers for 14 days** before Play allows a production release. Start a closed testing track
   early and invite friends. Internal testing does not count toward this.
7. Tick every box in the "Before you submit" list in `docs/PLAY_CONSOLE.md`.

---

## Part 5 — Tests on a real phone

Use the Play-installed internal testing build (Part 1) unless a step says otherwise. Tick each
one; anything that fails, note what you saw.

**Second chance (rewarded revive)**
1. Play Stack until a score of 10 or more, then miss.
2. "Keep going?" appears with a ring that empties over 5 seconds.
3. Tap the ad button and watch to the end: the run continues from where it was. (Tower: two floors
   fewer after a collapse, or one life back.)
4. Miss again in the same run: no second offer, straight to game over.
5. Another run: let the ring run out, or tap **No thanks**. You go to game over.

**2× gems (premium)**
1. Without premium, finish a run and note the gems earned.
2. Buy premium (Part 3), finish a similar run: game over shows "PREMIUM ×2" and about double gems.
3. ▶ 2× GEMS after a run (if offered) doubles on top of that, once.

**Interstitials (without premium)**
Rules: never on launch; only when starting a new run, once you have 8 or more runs in total, 3
minutes into the session, 4 runs *and* 4 minutes since the last full-screen ad, and never right
after a new best.
1. Fresh session: play 8 short runs quickly. No interstitial in the first 3 minutes.
2. Keep playing past 3 minutes: an interstitial shows when you tap to start a run.
3. After it, the next one needs 4 more runs and 4 more minutes.
4. Set a new best, then start a run: no interstitial that time.
5. With premium: never any interstitial or banner.

**Google sign-in**
1. Profile → **SIGN IN WITH GOOGLE** → pick your account. The panel says "Signed in with Google"
   with your email; gems, bests and name are unchanged.
2. Uninstall, reinstall from Play, sign in with the same account: progress comes back.
3. Second phone or emulator with the same account: bests merge (higher of both), themes combine.
4. SIGN OUT → confirm: back to guest, progress stays on the phone.
5. If sign-in fails with "developer error" or code 10, the SHA fingerprints from Part 1 step 7 are
   missing or `google-services.json` is stale.

**Also worth a look:** airplane mode during a run (the game keeps working, the score is sent
later); the gold icon after buying; sound and vibration toggles; the consent form (only shows in
the EEA, UK or Switzerland, or with `stackdrop.consentDebugEea=true` on a debug build).

---

## Part 6 — Merge the feature branch into main

The branch `feature/premium-perks-crashlytics` holds two commits (premium perks, Crashlytics).

**Through a pull request (recommended, gives you a record):**
1. Push the branch: `git push -u origin feature/premium-perks-crashlytics`
2. Open the pull request: `gh pr create --base main --fill`, or on GitHub click **Compare & pull
   request**.
3. Review the diff on GitHub, then **Merge pull request**.
4. Locally: `git switch main` then `git pull`.
5. Delete the branch: `git branch -d feature/premium-perks-crashlytics` and
   `git push origin --delete feature/premium-perks-crashlytics`.

**Directly (no pull request):**
```
git switch main
git pull
git merge --no-ff feature/premium-perks-crashlytics
git push
```

The older branches `feature/online-leaderboards-ads` and `feature/sound-premium-banner` are
already merged into main and can be deleted the same way.
