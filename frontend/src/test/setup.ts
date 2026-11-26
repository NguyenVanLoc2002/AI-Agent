import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
  window.localStorage.clear();
  window.sessionStorage.clear();
  // Hash routes (FE-UI-P2): every test starts at the root address, not at the screen an earlier test left open.
  window.history.replaceState(null, '', '/');
});
