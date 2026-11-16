# Stack Drop ads

Vertical (1080x1920, 60 fps) social ads rendered with [Remotion](https://www.remotion.dev/docs).
The gameplay is not screen-recorded. The game's modes, themes and screens are ported to TypeScript
and replay a scripted run frame by frame, so every ad shows the real game and renders the same way
every time. Sounds, music, font and icon are copied from the app by `scripts/prepare-assets.mjs`.

```sh
pnpm install
pnpm dev                  # Remotion Studio: preview and scrub every ad
pnpm render               # all ads to out/*.mp4
pnpm render TowerStreak   # just one
pnpm test                 # simulation tests
```

## The ads

| id | Hook | Idea |
| --- | --- | --- |
| `TowerStreak` | "5 perfects in a row = free floors" | A leaning tower, perfect drops climbing the scale, five free floors and the tower snapping straight. Twice, then a pull-back. |
| `TowerCollapse` | "Don't let it fall" | Drops creep off-center, the meter warns "LEANING RIGHT", the next drop tips the top over in slow motion. |
| `TowerThemes` | "Pick your vibe" | The Themes screen: tap a theme, the screen and tower restyle, then a short run in it. Sakura, Aurora, Toxic, Ice. |

## Layout

- `src/game/`: shared by both modes: themes (`palette.ts`, all 14), glow drawing (`neon.ts`),
  scenery (`backdrop.ts`: synthwave, the calm landscapes, ambient motion, skyline).
- `src/tower/`: tower mode (`TowerMode.kt`): simulation, renderer, scene and sounds.
- `src/stack/`: stack mode (`StackMode.kt`), the same pieces. No ad uses it right now.
- `src/ui/`: captions, tap dots, end card, music, and the Themes screen (`ThemesScreen.kt`).
- `src/ads/`: one file per ad.

## Making a new tower ad

A run is a list of drops passed to `simulateTower()` (see `src/tower/sim.ts`). Each number is where
the module lands, measured from the center of the floor below:

- `0`: perfect drop (five in a row start a streak)
- `0.8` / `-1.1`: lands that far off-center and pushes the tower's balance
- `2.15`: past half a module, slips off and costs a life
- `{ offset: 0, swings: 1 }`: lets the landing spot swing past once first

The crane swings exactly as in the game and the "tap" happens when a release would land on the
scripted spot. `startFloors` / `startOffsets` / `startCombo` start mid-run, `timeScale` adds slow
motion, and `showcaseAt` stops the crane and pulls the camera back. Events in the result (`release`,
`perfect`, `streakDone`, `warn`, `collapse`...) carry frame numbers, so captions and sounds line up.

Keep the important text between y 250 and 1500. TikTok and Reels cover the bottom ~20% and the
right edge with their own buttons.

Remotion is free for individuals and companies with up to 3 employees; larger teams need a
[company license](https://www.remotion.dev/license).
