// FE-UI-P1 (A-80, FE-UI-D08): viewport widths taken from the existing artboards, not new design.
// - 390: customer chat (F1-*.dc.html, 390×844).
// - 1024 / 1280 / 1440: staff layouts (R-1024, R-1280, RB-1280, RC/RD/RE-1024; 1440 artboards). From 1280 the AI panel
//   pushes the content and the sidebar stays open; below 1280 the sidebar collapses and panels overlay
//   (docs/design/specs/01-workflow-a-dac-ta-tuong-tac.md T04, T13).
// Staff screens below 1024 px have no artboard: whatever the shell does there is PROPOSED, not approved design.
//
// CSS custom properties cannot be used inside @media, so app/foundation.css repeats these numbers as `--bp-*` and in
// its media queries; foundation.test.ts fails when the two disagree.

export const BREAKPOINTS = {
  /** Narrowest customer layout that is designed. */
  customerMin: 390,
  /** Narrowest staff layout that is designed. */
  staffMin: 1024,
  /** From here the AI panel / drawer push the content; below it they overlay and the sidebar collapses. */
  panelPush: 1280,
  /** Widest artboard; content stops growing at --content-max (1440px). */
  wide: 1440,
} as const;

export type Breakpoint = keyof typeof BREAKPOINTS;

/** Media query for `window.matchMedia` that matches from the given breakpoint up. */
export function atLeast(bp: Breakpoint): string {
  return `(min-width: ${BREAKPOINTS[bp]}px)`;
}

/** Media query that matches below the given breakpoint (0.02px under it, so the two never overlap). */
export function below(bp: Breakpoint): string {
  return `(max-width: ${BREAKPOINTS[bp] - 0.02}px)`;
}
