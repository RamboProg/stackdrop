import { Composition } from "remotion";
import { TOWER_COLLAPSE_DURATION, TowerCollapse } from "./ads/TowerCollapse.tsx";
import { TOWER_STREAK_DURATION, TowerStreak } from "./ads/TowerStreak.tsx";
import { TOWER_THEMES_DURATION, TowerThemes } from "./ads/TowerThemes.tsx";
import { FORMAT } from "./lib/format.ts";

export const Root: React.FC = () => (
  <>
    <Composition id="TowerStreak" component={TowerStreak} durationInFrames={TOWER_STREAK_DURATION} {...FORMAT} />
    <Composition id="TowerCollapse" component={TowerCollapse} durationInFrames={TOWER_COLLAPSE_DURATION} {...FORMAT} />
    <Composition id="TowerThemes" component={TowerThemes} durationInFrames={TOWER_THEMES_DURATION} {...FORMAT} />
  </>
);
