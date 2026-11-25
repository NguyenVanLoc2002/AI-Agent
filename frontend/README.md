# APAS frontend (EP-10a)

Web demo of AutoParts Agent Sandbox: login, a role-based shell and screen F5 · OP-01 "Công tắc trợ lý AI" (UC-08). Synthetic data only. The backend is the authority for every permission; what the shell shows per role is presentation only.

Decisions: `docs/DECISIONS_REQUIRED.md` A-67. Screen spec: `docs/design/specs/50-batch-f-man-hinh-thieu.md` §F5.

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

## How it talks to the backend
- Same origin: the Vite dev server forwards `/api` to the backend, so the backend needs no CORS. The HttpOnly session cookie is the only credential; the code never reads or stores it, and nothing goes into `localStorage`/`sessionStorage`.
- Every state-changing request first reads a fresh CSRF token from `GET /api/auth/csrf`.
- Errors: only the problem `code` and the server `traceId` are kept. The screen shows a fixed Vietnamese message and the reference `REQ-` + the first 8 characters of the trace ID, never the server text or the HTTP status.
- The session cookie is `Secure`. Browsers accept it over plain HTTP only on `localhost`/`127.0.0.1`, so open the dev server at `http://127.0.0.1:5173`.

## Design system
`src/design-system/` holds copies of the components this slice uses from the repo-root `design-system/` (decision 3A), with their source hashes in `sources.json`. `components/core/Icon.jsx` is the only modified copy (A-66: the title is a React `<title>` element, the icon lookup uses own properties only). To take a newer design-system version: copy the file, update its hash in `sources.json`, review the diff as product code, run `npm run ds:verify` and the tests.

## Not covered yet
Screens F1 (customer chat and quote), F2/F3 (staff inbox and conversation), F4 (security inspector) are later slices. No browser end-to-end test against the real backend yet; no lint or formatter configured.
