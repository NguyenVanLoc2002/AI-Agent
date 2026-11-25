import { describe, expect, it } from 'vitest';
import { fakeBackend, json } from './fakeBackend';

// The test helper itself (EP-10d security review L-6): a query string matches a route only by opt-in.
describe('fakeBackend routes', () => {
  it('answers 404 for a query string unless the route opts in with "?*"', async () => {
    fakeBackend({ 'GET /api/x': () => json(200, { plain: true }), 'GET /api/y?*': () => json(200, { any: true }) });

    expect((await fetch('/api/x')).status).toBe(200);
    expect((await fetch('/api/x?y=1')).status).toBe(404);
    expect(await (await fetch('/api/y?z=2')).json()).toEqual({ any: true });
    expect((await fetch('/api/y')).status).toBe(404);
  });
});
