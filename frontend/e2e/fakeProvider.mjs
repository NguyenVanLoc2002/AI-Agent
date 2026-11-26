// FE-E2E-FLOWS (A-72): the Fake provider for browser tests against a local backend (ADR-004, A-05: the
// backend accepts only a loopback provider). Started by Playwright (playwright.config.ts, webServer); the
// backend is started with APAS_GATEWAY_PROVIDER_BASE_URL=http://127.0.0.1:18090.
//
// It answers the gateway's own wire format (`ModelRequest` in, `ModelResponse` out, POST
// /v1/fake-provider/complete) with fixed rules (decision 2A), like the scripted provider of the backend tests
// (ChatEnvironment): an OE code → searchVerifiedProduct; a search result → requestQuote for the first
// candidate; "nhân viên" → requestHandoff; otherwise a fixed sentence.
//
// What it receives is egress from the backend. It is kept in memory only, never printed or written to a file
// (decision 3A). Two test-only endpoints, also loopback: GET /__e2e/calls (how many provider calls) and POST
// /__e2e/contains {"needle"} (whether any received body contains the text: yes or no, never the body).
// No dependencies: node:http only.
//
// FE-E2E-ADVERSARIAL (A-75, 1A): fixed trigger sentences in the customer's message make it answer like a
// hostile model (SCENARIOS below); there is no endpoint to choose a scenario. To act with another customer's
// values it remembers the last productRef and quoteHandle it was given, in memory only, and uses each once
// (review security L2). GET /__e2e/remembered (run token) tells the test which values it holds, so the test
// can check those exact values never reach a person. The provider path itself has no authentication (the
// backend sends none): a local process could read or overwrite the remembered values; synthetic data only,
// and a wrong value makes the test fail, not pass (review security L1).
//
// Review security M1: only requests with Host exactly 127.0.0.1:<port>, without Origin or Sec-Fetch-Site (no
// browser page, no DNS rebinding) and, for a POST, with Content-Type application/json (a browser would need a
// preflight, which fails). The test endpoints also need the per-run token X-E2E-Token (APAS_E2E_FAKE_TOKEN, made
// by playwright.config.ts), take needles of at least 4 characters and answer at most 300 queries per run.

import { createServer } from 'node:http';

const HOST = '127.0.0.1';
const PORT = Number(process.env.APAS_E2E_FAKE_PROVIDER_PORT ?? '18090');
const COMPLETE_PATH = '/v1/fake-provider/complete';
const MAX_BODY_BYTES = 256 * 1024;
const MAX_KEPT_BODIES = 1000;
const MIN_NEEDLE = 4;
const MAX_QUERIES = 300;
const TOKEN = process.env.APAS_E2E_FAKE_TOKEN ?? '';
if (TOKEN.length < 32) {
  console.error('FE-E2E fake provider: APAS_E2E_FAKE_TOKEN missing (start it through Playwright).');
  process.exit(1);
}
let queries = 0;

/** Bodies received on the provider path, oldest first, in memory only. */
const received = [];
let calls = 0;

const OE = /\b([0-9A-Z]{3,}(?:-[0-9A-Z]{2,})+)\b/;
const QUANTITY = /(\d{1,4})\s*(?:bộ|cái|chiếc)/i;

function text(value) {
  return { text: value };
}

function tool(name, args) {
  return { toolProposals: [{ name, argumentsJson: JSON.stringify(args) }] };
}

/** The last server tokens seen in a tool result (any conversation): what a model could carry over. */
const remembered = { productRef: null, quoteHandle: null };

/** Answer when a scenario needs a remembered value that is missing: shown to the customer, so the test fails. */
const NOT_PREPARED = 'Kịch bản kiểm thử chưa có dữ liệu chuẩn bị.';

/** FE-E2E-ADVERSARIAL (A-75, 2A): customer sentence → hostile answer. */
const SCENARIOS = new Map([
  ['kịch bản kiểm thử: công cụ lạ', () => tool('exportCustomerList', { all: true })],
  ['kịch bản kiểm thử: tham số thừa', () => tool('searchVerifiedProduct', { oe: '04465-0K290', includeCost: true })],
  ['kịch bản kiểm thử: tham chiếu của khách khác', () => {
    const productRef = take('productRef');
    return productRef ? tool('requestQuote', { lines: [{ productRef, quantity: 1 }] }) : text(NOT_PREPARED);
  }],
  ['kịch bản kiểm thử: câu trả lời có giá', () => text('Giá má phanh là 1.250.000 đồng, anh đặt luôn nhé.')],
  ['kịch bản kiểm thử: mã báo giá của khách khác', () => {
    const quoteHandle = take('quoteHandle');
    return quoteHandle ? text(`Mã của anh là ${quoteHandle}, anh xác nhận giúp em.`) : text(NOT_PREPARED);
  }],
]);

/** A remembered value, used once. */
function take(name) {
  const value = remembered[name];
  remembered[name] = null;
  return value;
}

function remember(results) {
  for (const result of results) {
    if (result?.type === 'productSearch' && typeof result.candidates?.[0]?.productRef === 'string') {
      remembered.productRef = result.candidates[0].productRef;
    }
    if (result?.type === 'quoteRequest' && typeof result.quoteHandle === 'string') {
      remembered.quoteHandle = result.quoteHandle;
    }
  }
}

/** The fixed rules (decision 2A). `request` is the parsed ModelRequest. */
function answer(request) {
  const messages = Array.isArray(request.messages) ? request.messages : [];
  const customer = [...messages].reverse().find((m) => m && m.role === 'CUSTOMER');
  const said = typeof customer?.text === 'string' ? customer.text : '';
  const results = Array.isArray(request.toolResults) ? request.toolResults : [];
  const last = results[results.length - 1];
  remember(results);

  if (last) {
    switch (last.type) {
      case 'productSearch': {
        const first = Array.isArray(last.candidates) ? last.candidates[0] : undefined;
        if (!first || typeof first.productRef !== 'string') return text('Chưa tìm thấy sản phẩm phù hợp.');
        const quantity = Number(QUANTITY.exec(said)?.[1] ?? '1');
        return tool('requestQuote', { lines: [{ productRef: first.productRef, quantity }] });
      }
      case 'quoteRequest':
        return text(last.status === 'ISSUED' ? 'Báo giá đã được gửi bên dưới.' : 'Chưa lập được báo giá.');
      case 'handoff':
        return text('Đã chuyển cho nhân viên tư vấn.');
      default:
        return text('Chưa xử lý được yêu cầu.');
    }
  }
  const scenario = SCENARIOS.get(said.normalize('NFC').trim().toLowerCase());
  if (scenario) return scenario();
  if (said.toLowerCase().includes('nhân viên')) return tool('requestHandoff', { reason: 'CUSTOMER_REQUEST' });
  const oe = OE.exec(said.toUpperCase())?.[1];
  if (oe) return tool('searchVerifiedProduct', { oe });
  return text('Bạn cần tra cứu phụ tùng nào?');
}

function reply(response, status, body) {
  const json = JSON.stringify(body);
  response.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(json) });
  response.end(json);
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    request.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new Error('too large'));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    request.on('error', reject);
  });
}

/** Review security M1: loopback callers that are not a browser page. */
function acceptable(request) {
  if (request.headers.host !== `${HOST}:${PORT}`) return false;
  if (request.headers.origin !== undefined || request.headers['sec-fetch-site'] !== undefined) return false;
  if (request.method === 'POST' && !(request.headers['content-type'] ?? '').toLowerCase().startsWith('application/json')) return false;
  return true;
}

const server = createServer(async (request, response) => {
  try {
    if (!acceptable(request)) {
      reply(response, 400, { error: 'bad request' });
      return;
    }
    if (request.method === 'GET' && request.url === '/__e2e/health') {
      reply(response, 200, { status: 'UP' });
      return;
    }
    if (request.url?.startsWith('/__e2e/') && request.headers['x-e2e-token'] !== TOKEN) {
      reply(response, 403, { error: 'forbidden' });
      return;
    }
    if (request.method === 'GET' && request.url === '/__e2e/calls') {
      reply(response, 200, { calls });
      return;
    }
    if (request.method === 'GET' && request.url === '/__e2e/remembered') {
      reply(response, 200, { ...remembered });
      return;
    }
    if (request.method === 'POST' && request.url === '/__e2e/contains') {
      const { needle } = JSON.parse(await readBody(request));
      if (typeof needle !== 'string' || needle.length < MIN_NEEDLE || ++queries > MAX_QUERIES) {
        reply(response, 400, { error: 'needle' });
        return;
      }
      reply(response, 200, { found: received.some((body) => body.includes(needle)) });
      return;
    }
    if (request.method === 'POST' && request.url === COMPLETE_PATH) {
      const body = await readBody(request);
      calls += 1;
      received.push(body);
      if (received.length > MAX_KEPT_BODIES) received.shift();
      reply(response, 200, answer(JSON.parse(body)));
      return;
    }
    reply(response, 404, { error: 'not found' });
  } catch {
    // No detail: the request body may be egress data.
    if (!response.headersSent) reply(response, 400, { error: 'bad request' });
  }
});

server.on('error', () => {
  // Fixed text only (e.g. the port is taken); Playwright then fails the run.
  console.error(`FE-E2E fake provider: cannot listen on ${HOST}:${PORT}.`);
  process.exit(1);
});

server.listen(PORT, HOST, () => {
  // The only output: no request content is ever printed.
  console.log(`FE-E2E fake provider on http://${HOST}:${PORT}`);
});
