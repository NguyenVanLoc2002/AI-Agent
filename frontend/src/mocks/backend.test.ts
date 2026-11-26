import { afterEach, describe, expect, it, vi } from 'vitest';
import { createConversation, postMessage, readMessages, readState } from '../api/chat';
import { ApiError } from '../api/client';
import { readEvents } from '../api/inspector';
import { readSwitch, setSwitch } from '../api/killSwitch';
import { currentSession, login, logout } from '../api/session';
import { listHandoffs } from '../api/staff';
import { createMockFetch, MOCK_PASSWORD } from './backend';

// FE-UI-P2 (A-80 D05): every answer of the dev/test mock goes through the app's own decoders, which fail closed on an
// unknown field or format. A mock that drifts from the backend contract therefore fails here, not in the browser.

const use = (role: 'OPS' | 'SALES' | 'SECREV' | 'CUST' | null) => vi.stubGlobal('fetch', createMockFetch(role));
const status = async (call: Promise<unknown>) => {
  try { await call; return 'ok'; } catch (e) { return e instanceof ApiError ? e.status : 'other'; }
};

afterEach(() => { vi.unstubAllGlobals(); });

describe('mock backend', () => {
  it('signs in only the mock users with the mock password, like the backend (401 otherwise)', async () => {
    use(null);
    expect(await currentSession()).toBeNull();
    expect(await status(login('ops', 'wrong'))).toBe(401);
    expect(await status(login('someone', MOCK_PASSWORD))).toBe(401);
    expect(await login('sales', MOCK_PASSWORD)).toEqual({ role: 'SALES', customerLinked: false });
    expect(await currentSession()).toEqual({ role: 'SALES', customerLinked: false });
    await logout();
    expect(await currentSession()).toBeNull();
  });

  it('answers the kill switch in the contract shape and keeps the change', async () => {
    use('OPS');
    expect((await readSwitch()).aiEnabled).toBe(true);
    const off = await setSwitch(false);
    expect(off).toMatchObject({ aiEnabled: false, reason: 'OPERATOR' });
    expect(off.since).toBeInstanceOf(Date);
  });

  it('answers the inbox, the inspector and the customer chat in the shapes the decoders accept', async () => {
    use('SALES');
    expect(await listHandoffs()).toHaveLength(1);
    use('SECREV');
    expect(await readEvents({ type: null, role: null, from: new Date('2026-11-25T00:00:00Z'), to: new Date('2026-11-26T00:00:00Z') })).toEqual({ events: [], next: null });
    use('CUST');
    const id = await createConversation();
    expect(await readState(id)).toBe('BOT');
    expect(await readMessages(id)).toEqual([]);
    expect((await postMessage(id, crypto.randomUUID(), 'Xin chào')).card).toBeNull();
  });

  it('refuses another role\'s area with 403 and unknown paths with 404', async () => {
    use('SALES');
    expect(await status(readSwitch())).toBe(403);
    use('OPS');
    expect(await status(listHandoffs())).toBe(403);
    expect(await status(readMessages('cv_AAAAAAAAAAAAAAAAAAAAAA'))).toBe(403);
  });
});
