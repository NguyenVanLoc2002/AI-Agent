// FE-E2E (A-71): stops before any browser starts when the run cannot be meaningful. The checks name only
// what is missing; they never print the password or any server answer body.

const LOOPBACK = ['127.0.0.1', 'localhost', '[::1]'];

export default async function globalSetup(): Promise<void> {
  const password = process.env.APAS_DEMO_PASSWORD ?? '';
  if (password.length < 12) {
    throw new Error('FE-E2E: set APAS_DEMO_PASSWORD (at least 12 characters, the same value the backend was started with).');
  }
  const backend = process.env.APAS_BACKEND_URL ?? 'http://127.0.0.1:8080';
  if (!LOOPBACK.includes(new URL(backend).hostname)) {
    throw new Error('FE-E2E: APAS_BACKEND_URL must point to a loopback address.');
  }
  let healthy = false;
  try {
    const response = await fetch(new URL('/actuator/health', backend), { redirect: 'error', signal: AbortSignal.timeout(5_000) });
    const body: unknown = response.ok ? await response.json() : null;
    healthy = typeof body === 'object' && body !== null && (body as { status?: unknown }).status === 'UP';
  } catch {
    healthy = false;
  }
  if (!healthy) {
    throw new Error(`FE-E2E: the backend at ${backend} is not up. Start PostgreSQL and the backend with profile "local" first (frontend/README.md).`);
  }
}
