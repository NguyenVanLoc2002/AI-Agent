# APAS frontend (EP-10a)

Web demo of AutoParts Agent Sandbox: login, a role-based shell, screen F5 · OP-01 "Công tắc trợ lý AI" (UC-08, EP-10a) the customer chat F1 · CH-01..CH-04 with the quote card and its confirmation (UC-01, UC-03/04 customer side, EP-10b), the staff inbox F2 · HO-01 with the held conversation F3 · HO-02 (UC-05/06, EP-10c), and the Security Inspector F4 · SI-01 (UC-07, SECREV, EP-10d). Synthetic data only. The backend is the authority for every permission; what the shell shows per role is presentation only.

Decisions: `docs/DECISIONS_REQUIRED.md` A-67 (EP-10a), A-68 (EP-10b), A-69 (EP-10c), A-70 (EP-10d). Screen spec: `docs/design/specs/50-batch-f-man-hinh-thieu.md` §F1, §F2, §F3, §F4, §F5.

## Requirements
- Node.js 22.12 or newer, npm (dependencies pinned exactly in `package.json`, locked in `package-lock.json`).
- The backend running locally with the `demo` profile on `127.0.0.1:8080` (see the root `README.md`).

## Commands
```sh
npm ci              # install exactly what package-lock.json lists
npm run dev         # http://127.0.0.1:5173, /api forwarded to the backend (APAS_BACKEND_URL to change it)
npm test            # Vitest + Testing Library + axe-core (jsdom)
npm run typecheck   # tsc, app code and vite.config.ts separately
npm run build       # typecheck, then a production bundle in dist/
npm run ds:verify   # copies in src/design-system/ still match design-system/ (sources.json)
```

## Browser tests against the real backend (FE-E2E, A-71; FE-E2E-FLOWS, A-72)
Playwright (Chromium only) in `e2e/`, run by hand. They cover OPS sign-in and the AI switch off (with the confirmation) and on, the switch refused (403) to SALES, SECREV and customers in their own browser session, a lost session, sign-out ending the server session, and the generic wrong-password message (A-71). `flows.spec.ts` (A-72) covers the customer chat with a quote and its confirmation, a handoff the other team cannot see, the staff reply and resolve, a chat while the AI is off, and the Security Inspector. `adversarial.spec.ts` (A-75) makes the Fake provider answer like a hostile model (an unknown tool, an extra argument, another customer's productRef, a price or another customer's quote handle in the answer) and checks that the backend refuses each one: no quote card, the blocked text and the model's tokens never reach the customer's or the staff's browser, the conversation waits for staff with the right reason.

The AI answers come from the Fake provider `e2e/fakeProvider.mjs` (Node, no dependencies), which Playwright starts on `127.0.0.1:18090`. It answers with fixed rules, keeps what it receives in memory only and never prints it. It refuses requests from a browser page, another `Host` or without JSON, and its test endpoints (call count, "did the model receive this text", the values a hostile scenario holds) need a token Playwright makes for each run.

Once per machine: `npx playwright install chromium` (downloads the browser; Playwright 1.65.1 installs Chromium revision 1255). Install dependencies with `npm ci`; do not use `--with-deps` or a custom `PLAYWRIGHT_DOWNLOAD_HOST`.

The tests run against the deployed form of the app (FE-DEPLOY, A-74): the backend serving the built bundle.

1. In `frontend/`: `npm ci && npm run build`. In `backend/`: `./mvnw -Pwith-frontend -DskipTests clean package` (copies `frontend/dist` into the jar; fails without `dist/index.html`, or when `dist` holds a source map or any file but `index.html` and `assets/`). A build without `-Pwith-frontend` removes the frontend again. Stop running backends first on Windows (they lock the jar).
2. Start PostgreSQL and that backend with profile `local` (root `README.md`, `backend/README.md`), with `APAS_DEMO_PASSWORD` set and `APAS_GATEWAY_PROVIDER_BASE_URL=http://127.0.0.1:18090` (without it the chat tests fail: the AI hands every message to staff).
3. HTTPS project (optional, see below): start a second backend from the same jar with profiles `local,tls` on another port, and set `APAS_BACKEND_TLS_URL` (e.g. `https://127.0.0.1:18443`; use the IP, not `localhost`, so a browser never pins HSTS on every localhost port). Without it the project `chromium-tls` is NOT RUN and the run says so; set `APAS_E2E_REQUIRE_TLS=1` for a run meant as evidence, and the run stops instead.
4. In `frontend/`, with the same `APAS_DEMO_PASSWORD` in the environment (and `APAS_BACKEND_URL` if the backend is not on `http://127.0.0.1:8080`): `npm run e2e`.

The run stops before any browser starts when the password is missing, the backend is not up, or the backend serves another `index.html` than `frontend/dist` has now (build and package again). Playwright starts only the Fake provider on `127.0.0.1:18090`; it fails if the port is taken. The tests use the built bundle, not the dev server: the dev server runs React StrictMode effects twice, which reads a quote twice after a reload and can leave the screen with an outdated confirmation token. Loopback is checked, not that the backend is a local demo: do not point `APAS_BACKEND_URL` at a tunnel to another machine. The tests change the shared AI switch: every test puts it back on, and the global teardown checks once more and fails the run with "the AI switch could not be restored" if it cannot. After an interrupted run (Ctrl+C), sign in as OPS and check the switch. Do not run the tests while someone else uses the same backend. Traces and videos are off; a failed test leaves a screenshot and a page snapshot in `test-results/` (ignored by git, synthetic data only, delete after reading).

Rate limits are not loosened: a full run opens 3 new conversations for `cust-a`, 4 for `cust-b` and 2 for `cust-c` (every customer sign-in opens one), and the backend allows 5 per customer per hour. To run more than once an hour, recreate the local database (`docker compose down -v`, then up again) and restart the backend. Runs leave draft orders and resolved or open handoffs in the database (synthetic data).

What a run shows and what it does not: the UI and the API of the switch, the role checks and the session handling of one local backend. It is not SEC-06 evidence (the effect of the switch on the AI path within 1 minute is not measured), and clearing the cookie is not the server's 30 min / 8 h session limit.

## Security headers (FE-CSP, A-73; FE-DEPLOY, A-74)
The deployed app is served by the backend (`identity.SecurityConfiguration`): the same Content-Security-Policy and headers on every answer (pages, assets, API answers, errors), `Cache-Control: no-store` except for `/assets/**` (hashed names, cached for a year, `immutable`). With profile `tls` it also sends HSTS (one year) and the CSP `upgrade-insecure-requests`. A local HTTPS keystore is made with the JDK, outside the repository, never committed:

```sh
keytool -genkeypair -alias apas-local -keyalg EC -groupname secp256r1 -validity 30 -dname "CN=localhost" \
  -ext "SAN=dns:localhost,ip:127.0.0.1" -storetype PKCS12 -keystore <outside-the-repo>/apas-local.p12
export APAS_TLS_KEYSTORE=<outside-the-repo>/apas-local.p12 APAS_TLS_KEYSTORE_PASSWORD=<the password keytool asked for>
java -jar target/secure-erp-agent-0.1.0-SNAPSHOT.jar --spring.profiles.active=local,tls
```

The browser will warn about the self-signed certificate; a real deployment needs a real certificate, and behind a TLS-ending proxy HSTS needs `server.forward-headers-strategy` with trusted proxies (§2a FE-PROD-EDGE). Requests Tomcat rejects itself (e.g. an encoded `/` in the path) never reach Spring and get no security headers. The policy text exists twice and must stay equal: `config/securityHeaders.ts` (for `vite preview`) and `SecurityConfiguration.CONTENT_SECURITY_POLICY` (backend); each side has tests with the literal value.

`config/securityHeaders.ts` holds the headers of `vite preview`; `vite.config.ts` applies them.
- The built bundle (`npm run build`, then `npx vite preview`, `http://127.0.0.1:4173`) is sent with an enforced Content-Security-Policy (`default-src 'none'`; scripts, styles, fonts, images, requests and forms from the own origin only; no `<base>`, no plugins, no framing) and `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`, `Permissions-Policy`, `Cross-Origin-Opener-Policy` and `Cross-Origin-Resource-Policy` (`same-origin`). No HSTS: the demo runs over plain HTTP on loopback.
- The dev server (`npm run dev`) sends only `nosniff` and `Referrer-Policy`: Vite's hot reload needs an inline script, so there is no CSP in development.
- `vite preview` is for a quick local look at the bundle only; the deployed app is served by the backend (above).
- The browser tests fail on any CSP violation (`e2e/fixtures.ts`). A new inline script or style, an external font or a `data:` URL needs a reviewed change of the policy and of `config/securityHeaders.test.ts`.

## How it talks to the backend
- Same origin: the Vite dev server forwards `/api` to the backend, so the backend needs no CORS. The HttpOnly session cookie is the only credential; the code never reads or stores it, and nothing goes into `localStorage`/`sessionStorage`.
- Every state-changing request first reads a fresh CSRF token from `GET /api/auth/csrf`.
- Errors: only the problem `code` and the server `traceId` are kept. The screen shows a fixed Vietnamese message and the reference `REQ-` + the first 8 characters of the trace ID, never the server text or the HTTP status.
- Customer chat: the tab remembers the current conversation ID and the last quote reference in `sessionStorage` (no list endpoint exists); both are cleared on sign-out. Neither is a credential: the backend answers 404 to anyone but the owner. Quote amounts are never stored.
- Staff screens read the inbox, the messages and the prices only when needed (entry, after an action, on request): the backend audits every one of those reads. Prices are never stored.
- Security Inspector: events are read on entry (last 24 h), on "Áp dụng" and on "Tải thêm"; a trace only when asked and not already shown. Every read is audited (`INSPECTOR_READ`). Answers with an unknown field, a value outside the backend formats or a customer pseudonym are refused as a whole.
- The session cookie is `Secure`. Browsers accept it over plain HTTP only on `localhost`/`127.0.0.1`, so open the dev server at `http://127.0.0.1:5173`.

## Design system
`src/design-system/` holds copies of the components this slice uses from the repo-root `design-system/` (decision 3A), with their source hashes in `sources.json`. Modified copies (each with its reviewed hash `copySha256`): `components/core/Icon.jsx` (A-66: the title is a React `<title>` element, the icon lookup uses own properties only) and `components/ai/AIComposer.jsx` + `.d.ts` (A-68: accessible label, `maxLength`, no send on Enter while an input method is composing). To take a newer design-system version: copy the file, update its hash in `sources.json`, review the diff as product code, run `npm run ds:verify` and the tests.

## Not covered yet
Staff order screens (staff decision, reconciliation) need a design first (EP-10e). Browser tests against the real backend cover only sign-in, sessions and the AI switch (FE-E2E); no lint or formatter configured.
