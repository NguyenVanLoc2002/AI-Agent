import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { atLeast, below, BREAKPOINTS } from '../src/app/breakpoints';

// FE-UI-P1 (A-80): src/app/foundation.css stays a thin layer over design tokens (rule 50: no hard-coded colours or
// sizes), and its breakpoint numbers stay equal to src/app/breakpoints.ts (FE-UI-D08). jsdom has no layout engine,
// so the rendered responsive behaviour is checked with Playwright viewports once the shell (P2) uses these classes.
// The files are read from disk: under Vitest (`css: false`) a `?raw` CSS import is an empty string.

const read = (relative: string) => readFileSync(fileURLToPath(new URL(relative, import.meta.url)), 'utf8');
const stripComments = (css: string) => css.replace(/\/\*[\s\S]*?\*\//g, '');
const css = stripComments(read('../src/app/foundation.css'));
// Colour, spacing and size tokens live in tokens.css; the type roles (--type-*) in the component bundle.css.
const tokens = stripComments(read('../src/design-system/tokens.css')) + stripComments(read('../src/design-system/components/bundle.css'));
const mainSource = read('../src/main.tsx');

it('reads the real files (a vacuous pass on empty text is not possible)', () => {
  expect(css).toContain('.ap-page');
  expect(css).toContain('.ap-shell__main');
  expect(tokens).toContain('--space-8');
  expect(tokens).toContain('--type-button:');
  expect(mainSource).toContain('createRoot');
});

const defined = (source: string) => new Set([...source.matchAll(/(--[a-z0-9-]+)\s*:/g)].map((m) => m[1]));
const BP_VARS: Record<string, number> = {
  '--bp-customer-min': BREAKPOINTS.customerMin,
  '--bp-staff-min': BREAKPOINTS.staffMin,
  '--bp-panel-push': BREAKPOINTS.panelPush,
  '--bp-wide': BREAKPOINTS.wide,
};

describe('foundation.css', () => {
  it('uses no colour literal', () => {
    expect(css).not.toMatch(/#[0-9a-f]{3,8}\b/i);
    expect(css).not.toMatch(/\b(rgba?|hsla?|oklch|lab|lch)\(/i);
  });

  it('uses no length literal outside the breakpoints and the visually-hidden 1px', () => {
    const offending = css.split('\n')
      .filter((line) => /-?\d*\.?\d+(px|rem|em|vh|vw)\b/.test(line))
      // Breakpoints and the page constants of the approved artboards, both defined once in :root.
      .filter((line) => !/^\s*--(bp|page)-[a-z-]+:\s*\d+px;\s*$/.test(line))
      // The full viewport height of the shell is a layout fact, not a design value.
      .filter((line) => !/^\s*(min-)?height:\s*100vh;\s*$/.test(line))
      .filter((line) => !/^\s*@media\b/.test(line))
      .filter((line) => !/^\s*(width|height|margin):\s*-?1px;\s*$/.test(line));
    expect(offending).toEqual([]);
  });

  it('uses no raw font size or weight', () => {
    expect(css).not.toMatch(/font-(size|weight)\s*:/);
    expect(css).not.toMatch(/\bfont\s*:(?!\s*var\(--type-)/);
  });

  it('references only custom properties that tokens.css or the file itself defines', () => {
    const known = new Set([...defined(tokens), ...defined(css)]);
    const used = [...css.matchAll(/var\((--[a-z0-9-]+)/g)].map((m) => m[1]);
    expect(used.length).toBeGreaterThan(0);
    expect(used.filter((v) => !known.has(v))).toEqual([]);
  });

  it('declares the breakpoints with the same values as breakpoints.ts', () => {
    for (const [name, px] of Object.entries(BP_VARS)) {
      expect(css).toMatch(new RegExp(`${name}:\\s*${px}px;`));
    }
  });

  it('uses only breakpoint widths in media queries', () => {
    const allowed = new Set(Object.values(BREAKPOINTS).flatMap((px) => [px, px - 0.02]));
    const widths = [...css.matchAll(/@media[^{]*/g)].flatMap((m) => [...m[0].matchAll(/(\d+(?:\.\d+)?)px/g)].map((w) => Number(w[1])));
    expect(widths.filter((w) => !allowed.has(w))).toEqual([]);
  });

  it('is imported once by the app entry, after the tokens and the component styles', () => {
    const main = mainSource;
    const order = ['tokens.css', 'bundle.css', 'foundation.css'].map((f) => main.indexOf(f));
    expect(order.every((i) => i >= 0)).toBe(true);
    expect([...order].sort((a, b) => a - b)).toEqual(order);
  });
});

describe('breakpoints', () => {
  it('are the artboard widths in ascending order', () => {
    expect(Object.values(BREAKPOINTS)).toEqual([390, 1024, 1280, 1440]);
  });

  it('build media queries that meet without overlapping', () => {
    expect(atLeast('panelPush')).toBe('(min-width: 1280px)');
    expect(below('panelPush')).toBe('(max-width: 1279.98px)');
    expect(below('staffMin')).toBe('(max-width: 1023.98px)');
  });
});
