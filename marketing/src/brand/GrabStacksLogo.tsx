import { AbsoluteFill } from "remotion";
import { FONT } from "../ui/fonts.ts";
import { CREAM, MARK_BOX, markShapes, MUTED, NAVY, PINK } from "./mark.ts";

/**
 * GrabStacks logo stills. Sizes and spacing follow the studio splash's settled frame
 * (StudioSplash.kt, 1080-wide stage): the G about 520 px wide, "GrabStacks" at 120 px and
 * "STUDIO" at 34 px with wide tracking underneath.
 */

export type LogoProps = { background: boolean };

const Mark: React.FC<{ height: number }> = ({ height }) => (
  <svg
    height={height}
    width={(height * MARK_BOX.w) / MARK_BOX.h}
    viewBox={`${MARK_BOX.x} ${MARK_BOX.y} ${MARK_BOX.w} ${MARK_BOX.h}`}
    style={{ display: "block" }}
  >
    {markShapes().map((s, i) => (
      <polygon key={i} points={s.points} fill={s.fill} />
    ))}
  </svg>
);

/** Text whose letter spacing is shared on both sides of every glyph, as Android draws it. */
const Tracked: React.FC<{ size: number; spacing: number; color?: string; children: React.ReactNode }> = ({
  size,
  spacing,
  color,
  children,
}) => (
  <div
    style={{
      fontFamily: FONT,
      fontWeight: 500,
      fontSize: size,
      lineHeight: 1,
      letterSpacing: `${spacing}em`,
      paddingLeft: `${spacing}em`,
      color,
      whiteSpace: "nowrap",
    }}
  >
    {children}
  </div>
);

/** The G, then "GrabStacks" (cream + pink) and "STUDIO", stacked and centered. */
export const GrabStacksLogo: React.FC<LogoProps> = ({ background }) => (
  <AbsoluteFill style={{ background: background ? NAVY : undefined, alignItems: "center", justifyContent: "center" }}>
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", transform: "scale(1.3)" }}>
      <Mark height={603} />
      <div style={{ height: 157 }} />
      <Tracked size={120} spacing={0.04}>
        <span style={{ color: CREAM }}>Grab</span>
        <span style={{ color: PINK.top }}>Stacks</span>
      </Tracked>
      <div style={{ height: 22 }} />
      <Tracked size={34} spacing={0.5} color={MUTED}>
        STUDIO
      </Tracked>
    </div>
  </AbsoluteFill>
);

/** Just the G, sized so a circular profile-picture crop keeps all of it. */
export const GrabStacksMark: React.FC<LogoProps> = ({ background }) => (
  <AbsoluteFill style={{ background: background ? NAVY : undefined, alignItems: "center", justifyContent: "center" }}>
    <Mark height={1024 * 0.6} />
  </AbsoluteFill>
);
