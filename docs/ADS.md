# Ad behaviour

How ads work in Stack Drop. The between-run rules live in
`app/src/main/java/com/stackdrop/game/ads/InterstitialPacing.kt`, and `Game.kt` applies them.

## Ad types

| Ad | Where it shows | Player's choice? |
|---|---|---|
| Banner | Strip below the game, always there | No |
| Rewarded | "Keep going?", ▶ 2× GEMS, ▶ +25 | Yes, always opt-in |
| Interstitial | Between runs, as the next run starts | No |

Interstitials and rewarded ads each have a normal and a high value version. See [High value ads](#high-value-ads).

## Between-run interstitials

Both modes are endless, so every run ends in a loss. Runs are judged by score instead:

- **Failed run:** score below 10.
- **Successful run:** score of 10 or more.

### When an ad is owed

- After every failed run.
- After every 5th successful run in a row, counted separately for each mode.

A failed run resets the count for its own mode only. Losing in Tower does not touch the Stack count.
Quitting from pause counts as a normal ended run, judged by its score.

### When an owed ad can show

An owed ad shows when the player starts a run with Retry or a mode card, never mid-game or when
entering the menu. All of these must be true:

- The player has at least 8 lifetime runs.
- At least 60 seconds have passed since the last interstitial.
- No rewarded ad has shown since the last run started.
- The ad is already loaded. The game never waits for one.
- The player does not have premium.

If any of these fail, the run starts without an ad. The ad stays owed and is tried again at the next
run start.

## High value ads

A high value ad comes from a separate AdMob ad unit with a high eCPM floor, so it pays more but fills
less often.

### Fresh new best

When a run beats the mode's best and "Keep going?" appears:

- **Player accepts:** they get the high value rewarded ad, or the normal one if none is loaded. The
  run continues as usual.
- **Player skips:** see below.

"Keep going?" appears at most once per run, needs a score of 10 or more, and needs a rewarded ad
loaded. It lasts 5 seconds.

### Skipping on a fresh new best

A skip means tapping **No thanks**, pressing Back, letting the 5 seconds run out, or closing the ad
without earning the reward.

1. The game-over screen and new-best celebration play as normal.
2. At the next run start, the high value interstitial shows, or the normal one if none is loaded.
3. The grace period starts.

The ad is never shown straight after the skip, so it doesn't open under the player's tap.

If no ad can show at that run start, it is dropped and grace still starts. This happens if the player
has under 8 lifetime runs, is inside the 60 seconds, has no ad loaded, or has seen a rewarded ad, such
as 2× GEMS or a part-watched revive.

Skipping an offer on a run that is **not** a new best does nothing special.

### Grace period

- The next 3 failed runs owe no ad, in any mode.
- Any ad still owed when grace starts is dropped.
- Successful runs do not use up grace.
- The 5-in-a-row ad still shows during grace.

## Rewarded ads

| Placement | Where | Reward | Limits |
|---|---|---|---|
| Second chance | "Keep going?" when a run ends | The run continues | Once per run; score of 10 or more |
| 2× gems | Game-over screen | Gems from that run are doubled | Once per run; 3 or more gems earned |
| Free gems | Themes screen | +25 gems | 5 per day |

Rewards are applied after the ad closes, so play resumes smoothly.

## Premium

Premium players get no banner and no interstitials. Rewarded ads stay available as an option.

## Other rules

- **Launch:** no full-screen ad shows on launch, because nothing is owed until a run ends in that
  session.
- **Session only:** streak counts, grace and owed ads reset when the app closes.
- **Consent:** players in the EEA, UK and Switzerland see the consent form, and ads load only once
  consent allows them.
- **Sound:** game sound effects are muted during full-screen ads, and ad sound is off when the game's
  sound is off.
- **Tap guard:** taps in the 0.3 seconds after an ad closes are ignored, so closing an ad can't drop a
  block.
- **Preloading:** the game keeps one ad of each kind loaded and replaces it after 55 minutes. A failed
  load retries after 60 seconds, or 5 minutes for high value units.

## Example

A player with 20 lifetime runs and a best of 15 in Stack, with no ad in the last minute:

| Run | Score | What happens at the next run start |
|---|---|---|
| 1 | 6 | Interstitial: failed run |
| 2 | 12 | No ad: 1 in a row |
| 3 | 18, skips "Keep going?" | High value interstitial, grace starts: 3 failed runs left |
| 4 | 4 | No ad: 2 left, count reset to 0 |
| 5 | 3 | No ad: 1 left |
| 6 | 7 | No ad: 0 left |
| 7 | 5 | Interstitial: grace is over |

This assumes each ad is at least 60 seconds after the last one.

## Settings

Constants are in `InterstitialPacing.kt` unless noted.

| Setting | Value | Constant |
|---|---|---|
| Score for a successful run | 10 | `SUCCESS_SCORE` |
| Successful runs in a row per ad | 5 | `SUCCESSES_BETWEEN` |
| Failed runs covered by grace | 3 | `GRACE_RUNS` |
| Minimum gap between interstitials | 60 seconds | `GAP_MS` |
| Lifetime runs before interstitials | 8 | `MIN_TOTAL_RUNS` |
| Score for "Keep going?" | 10 | `REVIVE_MIN_SCORE` in `Game.kt` |

`SUCCESS_SCORE` and `REVIVE_MIN_SCORE` are separate, so changing one does not change the other.

## Ad units

| Ad unit | Gradle property |
|---|---|
| Banner | `stackdrop.admobBannerId` |
| Rewarded | `stackdrop.admobRewardedId` |
| Rewarded, high value | `stackdrop.admobRewardedHighId` |
| Interstitial | `stackdrop.admobInterstitialId` |
| Interstitial, high value | `stackdrop.admobInterstitialHighId` |

Debug builds always use Google's test ads. Setup steps are in `NEXT_STEPS.md`, Part 2.
