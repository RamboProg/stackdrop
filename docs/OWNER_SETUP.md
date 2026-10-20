# Stack Drop — Owner setup (Firebase)

Steps only you can do. Everything here is one-time unless noted. Until step 3 is done the app
builds and plays fully offline; nothing online happens.

## 1. Create the Firebase project

1. Open the [Firebase console](https://console.firebase.google.com) → **Add project** → name it **Stack Drop**.
   Google Analytics is optional; you can leave it off.
2. In the project, click the **Android** icon to add an app. Package name: `com.stackdrop.game`.
3. Fingerprints (needed for Google sign-in in Phase 3, harmless to add now). See
   [Fingerprints](#fingerprints) below for the full steps.

### Fingerprints

Google sign-in only works for builds whose signing certificate is registered in Firebase. Each
signing key has two fingerprints, SHA-1 and SHA-256; add both for every key.

**Debug key** (what **Run ▶** uses; it lives in `%USERPROFILE%\.android\debug.keystore`):

1. Open the **Gradle** tool window (elephant icon on the right edge).
2. Click **Execute Gradle Task** (the elephant-with-play icon in that window's toolbar), type
   `signingReport`, press **Enter**.
   (If you prefer the tree: **StackDrop → app → Tasks → android → signingReport**. If the Tasks folder
   is missing, use Execute Gradle Task instead.)
3. In the **Run** panel find the block that starts with `Variant: debug`. Copy the `SHA1:` and
   `SHA-256:` lines (the hex after the colon).
4. Firebase console → **⚙ Project settings → General → Your apps →** the Android app →
   **Add fingerprint** → paste the SHA-1 → **Save**. Click **Add fingerprint** again for the SHA-256.

If the Gradle task gives you trouble, the same values come from Android Studio's bundled keytool
(run in **View → Tool Windows → Terminal**):

```
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

**Release (upload) key**, when you are ready to publish:

1. **Build → Generate Signed App Bundle or APK → Android App Bundle → Create new…** keystore.
   Store the `.jks` file and both passwords somewhere safe and backed up; losing them means you can
   no longer upload updates with that key.
2. Get its fingerprints with keytool (replace the path and alias with yours; it asks for the password):
   ```
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "D:\keys\stackdrop-upload.jks" -alias upload
   ```
3. Add the SHA-1 and SHA-256 in Firebase as above.

**Play App Signing key**, after your first upload to Play Console: Google re-signs the app with its
own key, so builds installed from Play carry a different certificate. Play Console → your app →
**Test and release → Setup → App signing** (called App integrity in some versions) → copy the
SHA-1 and SHA-256 under **App signing key certificate** and add both in Firebase.

**After adding fingerprints:** download `google-services.json` again (Project settings → Your apps)
and replace `app/google-services.json`. The new file contains the OAuth client entries Google
sign-in needs. Do this again after enabling the Google provider in step 2.

## 2. Turn on the services

1. **Build → Authentication → Get started → Sign-in method**: enable **Anonymous**.
   (Enable **Google** too and set the support email; Phase 3 uses it.)
2. **Build → Firestore Database → Create database**: **production mode**, location **us-central1**
   (or `nam5` multi-region). The Cloud Functions region in code is `us-central1`; keep them together.
3. **App Check**: **Build → App Check → Apps → your Android app → Play Integrity → Register**.
   Do **not** click **Enforce** yet.

## 3. Add the config file to the app

1. **Project settings → Your apps → google-services.json → Download**.
2. Put it at `app/google-services.json` (next to `app/build.gradle.kts`).
3. In Android Studio: **File → Sync Project with Gradle Files**, then run the app.

The file is not secret, but if this repository ever becomes public, add `app/google-services.json`
to `.gitignore` first.

## 4. Register the debug App Check token (each test device)

1. Run a **debug** build on the device.
2. In **Logcat**, search for `DebugAppCheckProvider`. You will see:
   `Enter this debug secret into the allow list in the Firebase Console for your project: <token>`
3. Firebase console → **App Check → Apps → ⋮ menu → Manage debug tokens → Add debug token**, paste it.

Enforce App Check for Firestore (and later Functions) only after the game has synced successfully
with the debug token for a few days. **App Check → APIs → Cloud Firestore → Enforce.**

## 5. Install the Firebase CLI and deploy the security rules

You need [Node.js LTS](https://nodejs.org) once. Then, in a terminal (Android Studio's
**View → Tool Windows → Terminal** works):

```
npm install -g firebase-tools
firebase login
cd backend
firebase use --add          # pick the Stack Drop project, alias "default"
firebase deploy --only firestore
```

`firebase use --add` replaces the placeholder in `backend/.firebaserc`. Redeploy rules with the last
command whenever `backend/firestore.rules` changes.

**Blaze plan:** Cloud Functions (Phase 3 onwards) require it. When you upgrade under
**⚙ → Usage and billing → Details & settings → Modify plan**, set a budget alert (for example $5/month)
in Google Cloud Billing → Budgets & alerts. Phase 2 works on the free Spark plan.

## 6. Local testing with the Emulator Suite (optional, recommended)

The emulators let you test sign-in and sync without touching real data.

1. Still needs `app/google-services.json` from step 3 (the app reads the project id from it).
2. Start them: `cd backend` then `firebase emulators:start`. Allow it through the Windows firewall
   if asked. The emulator UI is at <http://localhost:4000>.
3. In the project's `gradle.properties` add:
   ```
   stackdrop.useEmulators=true
   ```
   Sync Gradle and run a **debug** build on the **Android emulator**; it reaches your PC at `10.0.2.2`.
4. On a **physical phone** on the same Wi-Fi instead, also add your PC's LAN IP:
   ```
   stackdrop.emulatorHost=192.168.1.20
   ```
   and add that IP as another `<domain>` in `app/src/debug/res/xml/network_security_config.xml`.
5. Set `stackdrop.useEmulators=false` (or delete the line) to go back to the real project.
   Release builds never use the emulators.

## 7. Phase 2 test steps

1. **Offline:** turn on airplane mode, clear the app from recents, and launch. The game starts,
   plays, saves, claims the gift and buys themes exactly as before. No error appears.
2. **First sync:** turn airplane mode off and reopen the app. In the Firebase console:
   - **Authentication → Users** shows one **anonymous** user.
   - **Firestore → users → {that uid}** has `gems`, `bests`, `unlockedThemes`, `rev`, and so on.
3. **Push after play:** finish a run. About 3 seconds later the document's `gamesPlayed` and `rev`
   go up (refresh the console view).
4. **Pull and merge:** in the console, edit the document: set `rev` to a number higher than the
   current one and `gems` to 500. Force-stop the app (Settings → Apps → Stack Drop → Force stop) and
   reopen it. The menu shows 500 gems. Bests are never lowered by this: set a lower `bests.stack` and
   your local best stays.
5. **Offline changes catch up:** go into airplane mode, play a run, return to the home screen, turn
   airplane mode off and reopen. The document updates within a few seconds.
6. **Clear storage and restore:** this needs a Google account, which arrives in Phase 3. An anonymous
   account lives only on the phone, so clearing storage creates a new, empty one. That is expected.

## 8. Phase 3: Cloud Functions, Google sign-in and profiles

### 8.1 Before you start (one time)

- **Blaze plan** with a budget alert (section 5). Functions cannot deploy without it.
- **Google sign-in provider** enabled (Authentication → Sign-in method → Google).
- **Fingerprints** added and a fresh `app/google-services.json` downloaded (see [Fingerprints](#fingerprints)).
- **Debug App Check token** registered for every test device (step 4). Every Cloud Function here
  requires App Check, so calls from an unregistered debug build fail.
- **Android emulator image:** Google sign-in needs an image with **Google Play** (the system image
  name says "Google Play") and a Google account added in the emulator's Settings → Accounts.
- **Java** for the Firestore emulator (only needed for `npm run test:rules`): install a JDK 21
  (for example Eclipse Temurin) or point `JAVA_HOME` at Android Studio's
  `C:\Program Files\Android\Android Studio\jbr`.

### 8.2 Install, test and deploy the functions

In Android Studio's **Terminal**:

```
cd "D:\personal projects\work\mobile game\backend\functions"
npm install
npm test
```

`npm test` compiles the TypeScript and runs the name-filter and friend-code tests; every line should
say `ok`. Optionally run the security-rules tests (starts and stops the Firestore emulator itself):

```
npm run test:rules
```

Then deploy everything from the `backend` folder:

```
cd ..
firebase deploy --only functions,firestore
```

The first deploy takes a few minutes and may ask to enable Cloud Build, Artifact Registry and
Cloud Run APIs; answer **Y**. It ends with `Deploy complete!` and lists `claimName`, `setAvatar` and
`deleteAccount` in `us-central1`. If it asks whether to set up a cleanup policy for container
images, answer **Y** and keep the default (1 day) so old builds do not cost storage.

### 8.3 Phase 3 test steps

1. **Profile screen:** tap the avatar chip at the top-left of the menu. You see an avatar, CHOOSE A
   NAME, your bests, and "Guest account".
2. **Avatar:** tap the avatar a few times; it cycles through 12 presets. It is kept after restarting.
3. **Name:** tap CHOOSE A NAME, type a name, OK. Within a couple of seconds the name and a friend code
   like `K7Q2-M9XD` appear. In Firestore you see `profiles/{uid}`, `usernames/{name}` and
   `friendCodes/{code}`.
   - Try a 2-letter name, a name with `-`, and a rude word: each shows a friendly message.
   - Change the name 3 times; the 4th change that day is refused.
   - On a second account (or the emulator UI), try to take the same name: you get suggestions.
4. **COPY / SHARE:** COPY puts the code on the clipboard; SHARE opens the share sheet.
5. **Guest progress survives upgrading:** as a guest, note your gems and bests. Tap
   **SIGN IN WITH GOOGLE** and pick an account. The panel switches to "Signed in with Google" with
   your email; gems, bests, name and friend code are unchanged. In Authentication → Users the same
   uid now shows the Google provider.
6. **Second device merge:** on another device (or a wiped emulator), sign in with the same Google
   account. Bests become the higher of both devices and themes are combined.
7. **Sign out:** tap SIGN OUT and confirm. The panel returns to "Guest account" and your progress
   stays on the phone. Signing in again with Google restores the Google account's profile.
8. **Delete account:** tap DELETE ACCOUNT and confirm twice. You return to the menu as a fresh guest
   (settings kept). In Firestore, `profiles/{old uid}`, `usernames/...`, `friendCodes/...`,
   `users/{old uid}` and `private/{old uid}` are gone, and the user is removed from Authentication.
