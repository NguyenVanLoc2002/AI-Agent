import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expect, it } from 'vitest';

// FE-REACT19: the app root must be created with the error options that keep React from logging render errors with
// their message and stack (src/app/rootErrors.ts). main.tsx is not rendered by the unit tests, so its source is checked.
const main = readFileSync(join(process.cwd(), 'src', 'main.tsx'), 'utf8');

it('creates the root with ROOT_ERROR_OPTIONS', () => {
  expect(main).toContain("import { ROOT_ERROR_OPTIONS } from './app/rootErrors';");
  expect(main).toMatch(/createRoot\(root, ROOT_ERROR_OPTIONS\)/);
  expect(main.match(/createRoot\(/g)).toHaveLength(1);
});
