import { useEffect, useState } from "react";
import { cancelRender, continueRender, delayRender } from "remotion";
import { fontReady } from "./fonts.ts";

/** True once Fredoka can be drawn on a canvas; holds the render until then. */
export function useFontReady(): boolean {
  const [ready, setReady] = useState(false);
  const [handle] = useState(() => delayRender("Loading Fredoka for canvas text"));
  useEffect(() => {
    fontReady
      .then(() => {
        setReady(true);
        continueRender(handle);
      })
      .catch((e: unknown) => cancelRender(new Error(`Fredoka font failed to load: ${String(e)}`)));
  }, [handle]);
  return ready;
}
