import { readFileSync } from 'node:fs';
import { request as httpsRequest } from 'node:https';
import { fileURLToPath } from 'node:url';
import { BASE_URL, TLS_URL } from './env';

// FE-E2E (A-71): stops before any browser starts when the run cannot be meaningful. The checks name only
// what is missing; they never print the password or any server answer body.
// FE-DEPLOY (A-74, 6A): the backend must serve the bundle that is in frontend/dist now, so a run never tests an
// older build that happens to be in the jar.

async function healthy(base: string): Promise<boolean> {
  try {
    const response = await fetch(new URL('/actuator/health', base), { redirect: 'error', signal: AbortSignal.timeout(5_000) });
    const body: unknown = response.ok ? await response.json() : null;
    return typeof body === 'object' && body !== null && (body as { status?: unknown }).status === 'UP';
  } catch {
    return false;
  }
}

async function servedPage(base: string): Promise<string | null> {
  try {
    const response = await fetch(new URL('/', base), { headers: { Accept: 'text/html' }, redirect: 'error', signal: AbortSignal.timeout(5_000) });
    return response.ok ? await response.text() : null;
  } catch {
    return null;
  }
}

/**
 * GET over HTTPS to the local `tls` backend, whose keystore is self-signed: the certificate is not checked for this
 * loopback call only (TLS_URL is loopback, env.ts). Answers status and body, or null.
 */
function tlsGet(base: string, path: string, accept: string): Promise<{ status: number; body: string } | null> {
  return new Promise((resolve) => {
    const call = httpsRequest(new URL(path, base), { method: 'GET', headers: { Accept: accept }, rejectUnauthorized: false, timeout: 5_000 },
      (response) => {
        let body = '';
        response.setEncoding('utf8');
        response.on('data', (chunk: string) => { body += chunk; });
        response.on('end', () => resolve({ status: response.statusCode ?? 0, body }));
      });
    call.on('error', () => resolve(null));
    call.on('timeout', () => { call.destroy(); resolve(null); });
    call.end();
  });
}

export default async function globalSetup(): Promise<void> {
  const password = process.env.APAS_DEMO_PASSWORD ?? '';
  if (password.length < 12) {
    throw new Error('FE-E2E: set APAS_DEMO_PASSWORD (at least 12 characters, the same value the backend was started with).');
  }
  if (!(await healthy(BASE_URL))) {
    throw new Error(`FE-E2E: the backend at ${BASE_URL} is not up. Start PostgreSQL and the backend with profile "local" first (frontend/README.md).`);
  }

  let built: string;
  try {
    built = readFileSync(fileURLToPath(new URL('../dist/index.html', import.meta.url)), 'utf8');
  } catch {
    throw new Error('FE-E2E: frontend/dist is missing. Run npm run build, then package the backend with -Pwith-frontend (frontend/README.md).');
  }
  if ((await servedPage(BASE_URL)) !== built) {
    throw new Error(`FE-E2E: the backend at ${BASE_URL} does not serve the current frontend/dist. Package it again with -Pwith-frontend and restart it.`);
  }

  if (TLS_URL === null) {
    // Review QA 1: a run meant as evidence sets APAS_E2E_REQUIRE_TLS=1, so the HTTPS checks cannot be skipped silently.
    if (process.env.APAS_E2E_REQUIRE_TLS === '1') {
      throw new Error('FE-E2E: APAS_E2E_REQUIRE_TLS=1 but APAS_BACKEND_TLS_URL is not set (start the backend with profile tls).');
    }
    console.log('FE-E2E: APAS_BACKEND_TLS_URL is not set, the HTTPS project (e2e/tls.spec.ts) is NOT RUN.');
    return;
  }
  // Review QA 2: the HTTPS backend must be up and serve the same bundle.
  const health = await tlsGet(TLS_URL, '/actuator/health', 'application/json');
  if (health === null || health.status !== 200 || !health.body.includes('"status":"UP"')) {
    throw new Error(`FE-E2E: the HTTPS backend at ${TLS_URL} is not up (profiles local,tls; frontend/README.md).`);
  }
  const tlsPage = await tlsGet(TLS_URL, '/', 'text/html');
  if (tlsPage === null || tlsPage.body !== built) {
    throw new Error(`FE-E2E: the HTTPS backend at ${TLS_URL} does not serve the current frontend/dist. Restart it from the new jar.`);
  }
}
