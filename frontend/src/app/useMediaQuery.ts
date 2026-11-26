import { useEffect, useState } from 'react';

// Matches a media query from breakpoints.ts. Without matchMedia (old browsers, jsdom) it answers `fallback`,
// so the wide layout is the default and nothing breaks.

function matches(query: string, fallback: boolean): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function' ? window.matchMedia(query).matches : fallback;
}

export function useMediaQuery(query: string, fallback = false): boolean {
  const [match, setMatch] = useState(() => matches(query, fallback));
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return undefined;
    const list = window.matchMedia(query);
    const onChange = () => setMatch(list.matches);
    onChange();
    list.addEventListener('change', onChange);
    return () => list.removeEventListener('change', onChange);
  }, [query]);
  return match;
}
