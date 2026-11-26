// FE-UI-P2 (A-80 D05, P2-T10): a stand-in for the backend, for development and tests only. It is never part of the
// production bundle: main.tsx imports it behind `import.meta.env.DEV`, and `scripts/verify-no-mocks.mjs` fails the
// build if this marker reaches dist/. It answers only endpoints that exist in the backend today, with the shapes the
// app's own decoders accept (src/mocks/backend.test.ts runs every answer through them). Synthetic data only: no real
// customer, price, credential or trace. No business rule lives here; the real backend stays the authority.
export const MOCK_MARKER = 'APAS_MOCK_BACKEND_DEV_ONLY';

type Role = 'OPS' | 'SALES' | 'SECREV' | 'CUST';

/** Sign-in names of the mock (any other name, or a password other than MOCK_PASSWORD, is refused like the backend). */
export const MOCK_USERS: Readonly<Record<string, Role>> = { ops: 'OPS', sales: 'SALES', secrev: 'SECREV', cust: 'CUST' };
export const MOCK_PASSWORD = 'mock';

const CONVERSATION = 'cv_MockConversation000001';
const TRACE = '00000000-0000-4000-8000-00000000f00d';

interface State {
  role: Role | null;
  aiEnabled: boolean;
  since: string;
}

const AREAS: ReadonlyArray<readonly [string, Role]> = [
  ['/api/ops/', 'OPS'],
  ['/api/internal/', 'SALES'],
  ['/api/demo/security/', 'SECREV'],
  ['/api/demo/conversations', 'CUST'],
  ['/api/demo/quotes', 'CUST'],
];

// Every mock answer carries the marker in a header, so the marker string is referenced wherever this module is bundled
// (it cannot be dropped as dead code) and a developer can tell a mock answer in the network panel.
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', 'X-Apas-Mock': MOCK_MARKER } });
const problem = (status: number, code: string) =>
  new Response(JSON.stringify({ status, code, traceId: TRACE }), { status, headers: { 'Content-Type': 'application/problem+json', 'X-Apas-Mock': MOCK_MARKER } });

export function createMockFetch(initialRole: Role | null = null): typeof fetch {
  const state: State = { role: initialRole, aiEnabled: true, since: '2026-11-26T08:00:00Z' };

  return async (input, init) => {
    const url = new URL(String(input), 'http://mock.local');
    const method = (init?.method ?? 'GET').toUpperCase();
    const route = `${method} ${url.pathname}`;
    const body = typeof init?.body === 'string' ? init.body : '';

    if (route === 'GET /api/auth/csrf') return json(200, { headerName: 'X-CSRF-TOKEN', token: 'mock-csrf-token' });
    if (route === 'POST /api/auth/login') {
      const form = new URLSearchParams(body);
      const role = MOCK_USERS[form.get('username') ?? ''];
      if (!role || form.get('password') !== MOCK_PASSWORD) return problem(401, 'UNAUTHENTICATED');
      state.role = role;
      return json(200, { role, customerLinked: role === 'CUST' });
    }
    if (route === 'POST /api/auth/logout') { state.role = null; return new Response(null, { status: 204 }); }
    if (state.role === null) return problem(401, 'UNAUTHENTICATED');
    if (route === 'GET /api/auth/session') return json(200, { role: state.role, customerLinked: state.role === 'CUST' });

    // Each URL area answers only its role, like the backend's URL rules (RBAC_MATRIX EP-08a, EP-09a/b, A-62);
    // another role gets 403 before the path is looked at.
    const area = AREAS.find(([prefix]) => url.pathname.startsWith(prefix));
    if (area && area[1] !== state.role) return problem(403, 'FORBIDDEN');
    if (url.pathname === '/api/ops/kill-switch') {
      if (method === 'POST') {
        const enabled = (JSON.parse(body || '{}') as { enabled?: unknown }).enabled;
        if (typeof enabled !== 'boolean') return problem(400, 'VALIDATION');
        state.aiEnabled = enabled;
        state.since = new Date().toISOString();
      }
      return json(200, { aiEnabled: state.aiEnabled, reason: state.aiEnabled ? null : 'OPERATOR', since: state.since });
    }
    if (route === 'GET /api/internal/handoffs') {
      return json(200, [
        { handoffId: 1, conversationId: CONVERSATION, reasonCode: 'CUSTOMER_REQUEST', status: 'OPEN', openedAt: '2026-11-26T07:50:00Z', mine: false, followUpReasonCode: null },
      ]);
    }
    if (route === 'GET /api/demo/security/events') return json(200, { events: [], next: null });
    if (route === 'POST /api/demo/conversations') return json(201, { conversationId: CONVERSATION });
    if (route === `GET /api/demo/conversations/${CONVERSATION}`) return json(200, { state: 'BOT' });
    if (route === `GET /api/demo/conversations/${CONVERSATION}/messages`) return json(200, []);
    if (route === `POST /api/demo/conversations/${CONVERSATION}/messages`) {
      return json(200, { reply: 'Đây là câu trả lời mô phỏng, không có dữ liệu thật.', handedOff: false, duplicate: false, quoteCard: null });
    }
    return problem(404, 'NOT_FOUND');
  };
}
