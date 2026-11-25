// FE-E2E (A-71): the page origin under test. Loopback only: the Secure session cookie is accepted over plain
// HTTP only there (frontend/README.md). No imports, so playwright.config.ts can use it too.
export const BASE_URL = 'http://127.0.0.1:5173';
