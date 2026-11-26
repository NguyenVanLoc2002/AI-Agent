// Checks the design-system files copied into src/design-system/ against src/design-system/sources.json
// (EP-10a, decision 3A): every recorded file in the repo-root design-system/ still has the recorded hash,
// every copy that is not marked as modified is byte-identical to it, and a modified copy (e.g. Icon.jsx A-66,
// Tooltip.jsx A-80) still has its reviewed hash `copySha256`, so a later edit of it is never silent. Every file under
// the copied `components/` and `assets/` folders must have an entry (A-80 review L4), so an unreviewed copy fails too.
// Exit 1 on any difference.
import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');
const copyRoot = join(frontend, 'src', 'design-system');
const sourceRoot = join(frontend, '..', 'design-system');
const manifest = JSON.parse(readFileSync(join(copyRoot, 'sources.json'), 'utf8'));

// Text files are hashed with LF line endings, so a checkout with core.autocrlf does not fail the check.
const sha256 = (file) => {
  const bytes = readFileSync(file);
  const data = file.endsWith('.woff2') ? bytes : Buffer.from(bytes.toString('utf8').replace(/\r\n/g, '\n'), 'utf8');
  return createHash('sha256').update(data).digest('hex');
};
const problems = [];

for (const entry of manifest.files) {
  const source = join(sourceRoot, entry.path);
  const copy = join(copyRoot, entry.path);
  if (!existsSync(source)) problems.push(`source missing: ${entry.path}`);
  else if (sha256(source) !== entry.sha256) problems.push(`source changed since the copy: ${entry.path}`);
  if (!existsSync(copy)) problems.push(`copy missing: ${entry.path}`);
  else if (!entry.modified && sha256(copy) !== entry.sha256) problems.push(`copy differs without a recorded change: ${entry.path}`);
  else if (entry.modified && sha256(copy) !== entry.copySha256) problems.push(`modified copy changed since its review: ${entry.path}`);
}

const listed = new Set(manifest.files.map((entry) => entry.path));
const walk = (dir) => readdirSync(dir, { withFileTypes: true })
  .flatMap((d) => (d.isDirectory() ? walk(join(dir, d.name)) : [join(dir, d.name)]));
for (const folder of ['components', 'assets']) {
  const root = join(copyRoot, folder);
  if (!existsSync(root)) continue;
  for (const file of walk(root)) {
    const path = relative(copyRoot, file).split(sep).join('/');
    if (!listed.has(path)) problems.push(`copy without a sources.json entry: ${path}`);
  }
}

if (problems.length) {
  for (const p of problems) console.error(p);
  process.exit(1);
}
console.log(`design-system copies OK: ${manifest.files.length} files`);
