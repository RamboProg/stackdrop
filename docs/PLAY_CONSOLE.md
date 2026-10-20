# Stack Drop — Play Console answers

What to enter in Play Console, based on what the app actually does. The AdMob rows follow Google's
"Google Mobile Ads SDK data disclosure" guidance; re-check that page before submitting, since Google
updates it.

## Before you submit

- [ ] Privacy policy hosted (draft: `docs/store/privacy-policy.md`) and its URL in `ui/Links.kt`
- [ ] Account deletion page hosted (draft: `docs/store/account-deletion.md`) and its URL in `ui/Links.kt`
- [ ] Real AdMob IDs in `gradle.properties`: `stackdrop.admobAppId`, `stackdrop.admobBannerId`,
      `stackdrop.admobRewardedId`, `stackdrop.admobInterstitialId` (without them release builds show
      test ads and earn nothing)
- [ ] One-time product `premium` created and active (Monetize with Play → Products), and a payments
      profile set up (see `OWNER_SETUP.md` section 12)
- [ ] GDPR consent message published in AdMob → Privacy & messaging
- [ ] `app-ads.txt` on the developer website listed in Play Console (AdMob → Apps → app-ads.txt)
- [ ] Play App Signing SHA-1 and SHA-256 added in Firebase (see `OWNER_SETUP.md`, Fingerprints)
- [ ] App Check enforced for Firestore and Cloud Functions once release builds sync correctly
- [ ] Debug App Check tokens deleted (App Check → Manage debug tokens)
- [ ] Google "G" on the sign-in button replaced with the official asset

## App content

| Section | Answer |
|---|---|
| Privacy policy | Your hosted URL |
| Ads | **Yes, my app contains ads** |
| App access | **All functionality is available without special access** (players start as guests; Google sign-in is optional) |
| Target audience | **13–15, 16–17, 18 and over**. Do not select any age under 13 (that would bring in the Families policy). |
| Content rating | Answer the questionnaire honestly. Relevant answers: no violence, no gambling; **users can interact or exchange content: Yes** (display names are visible on leaderboards and to friends); no chat; no location sharing. |
| Advertising ID | **Yes**, used for **Advertising or marketing** (the Google Mobile Ads SDK adds the `AD_ID` permission) |
| Government apps, financial features, health | No |

## Data safety

**Data collection and security**
- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes**
- Do you provide a way for users to request that their data is deleted? **Yes** (in-app, plus your deletion URL)

**Data types**

| Category → type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Personal info → Name (display name) | Yes | No | Optional | App functionality |
| Personal info → Email address (Google sign-in) | Yes | No | Optional | Account management |
| Personal info → User IDs (account ID) | Yes | No | Required | App functionality, Account management, Fraud prevention, security and compliance |
| App activity → App interactions (scores, run statistics) | Yes | No | Required | App functionality, Fraud prevention, security and compliance |
| App activity → Other user-generated content (friend list, friend requests) | Yes | No | Optional | App functionality |
| Location → Approximate location (AdMob, from IP) | Yes | Yes | Required | Advertising or marketing, Fraud prevention |
| App info and performance → Crash logs, Diagnostics (AdMob) | Yes | Yes | Required | Advertising or marketing, Analytics |
| Device or other IDs (advertising ID, AdMob; App Check) | Yes | Yes | Required | Advertising or marketing, Fraud prevention, security and compliance |

| Financial info → Purchase history (premium, through Google Play) | Yes | No | Optional | App functionality |

Not collected: payment card or bank details (Google Play handles payment), health, messages,
photos or videos, audio, files, calendar, contacts, precise location, web browsing, installed apps.

"Shared" above means passed to Google for advertising. Data sent to Firebase is processed by Google
on your behalf as a service provider, which Play does not count as sharing.

## Store listing

- Category: Games → Casual (or Arcade)
- Contact email: required; use the address in the privacy policy
- Website: the domain that hosts `app-ads.txt`
