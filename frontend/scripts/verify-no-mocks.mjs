// FE-UI-P2 (A-80 D05): fails when anything of src/mocks/ reached the production bundle in dist/. Runs as part of
// `npm run build`. It looks for the marker string and the names of src/mocks/ and for any emitted file named like a
// mock chunk. Exit 1 on any finding.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(frontend, 'dist');
const NEEDLES = ['APAS_MOCK_BACKEND_DEV_ONLY', 'createMockFetch', 'installMocks', 'mock-csrf-token'];

if (!existsSync(join(dist, 'index.html'))) {
  console.error('verify-no-mocks: dist/index.html missing, build first');
  process.exit(1);
}

const walk = (dir) => readdirSync(dir, { withFileTypes: true }).flatMap((d) => (d.isDirectory() ? walk(join(dir, d.name)) : [join(dir, d.name)]));
const problems = [];
for (const file of walk(dist)) {
  const path = relative(frontend, file).split('\\').join('/');
  if (/mock/i.test(path)) problems.push(`mock-named file in the bundle: ${path}`);
  // Every file except the fonts is searched (review security M2: .mjs, .txt, .svg … too).
  if (/\.woff2?$/.test(file)) continue;
  const text = readFileSync(file, 'utf8');
  for (const needle of NEEDLES) if (text.includes(needle)) problems.push(`"${needle}" found in ${path}`);
}

if (problems.length) {
  for (const p of problems) console.error(p);
  process.exit(1);
}
console.log('verify-no-mocks: production bundle has no mock code');
