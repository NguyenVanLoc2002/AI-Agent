import { expect, test } from '@playwright/test';
import { callApi, ensureAiOn, expectProblem, readAiSwitchAsOps, setAiSwitchAsOps, signInWithApi, signInWithForm, type DemoUser } from './support';

// FE-E2E (A-71) · RBAC for the AI switch (A-44, FR-013): the shell hides the switch from other roles, but that
// is presentation only. The proof is the backend: the same browser session calling the switch API directly
// gets 403 with only the problem fields, in both directions (off while on, on while off), and the switch
// is unchanged. A CSRF failure is also a 403 `FORBIDDEN`, so the first test is the positive control: the
// very same calls, with a fresh CSRF token, succeed for OPS (review QA M4).

test.beforeAll(async ({ browser }) => { await ensureAiOn(browser); });
test.afterEach(async ({ browser }) => { await ensureAiOn(browser); });

test('control: the same API calls succeed for OPS', async ({ page }) => {
  await signInWithApi(page, 'ops-1');
  expect((await callApi(page, 'GET', '/api/ops/kill-switch')).status).toBe(200);
  // Idempotent: the AI is on already, so nothing changes.
  expect((await callApi(page, 'POST', '/api/ops/kill-switch', { enabled: true })).status).toBe(200);
});

const OTHERS: { user: DemoUser; screen: string }[] = [
  { user: 'sales-1', screen: 'Hộp chờ nhân viên' },
  { user: 'secrev-1', screen: 'Sự kiện bảo mật' },
  { user: 'cust-a', screen: 'Tư vấn phụ tùng' },
];

for (const { user, screen } of OTHERS) {
  test(`${user} has no AI switch screen and the backend refuses the switch`, async ({ page, browser }) => {
    await signInWithForm(page, user);
    await expect(page.getByRole('button', { name: 'Đăng xuất' })).toBeVisible();
    await expect(page.getByRole('heading', { name: screen, level: 1 })).toBeVisible();
    // No switch entry in the navigation (the customer has no sidebar at all). Page text is not checked:
    // the Security Inspector's event-type filter names the switch events "Công tắc AI".
    await expect(page.getByRole('navigation').getByRole('button', { name: 'Công tắc AI' })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'Công tắc trợ lý AI' })).toHaveCount(0);

    const read = await callApi(page, 'GET', '/api/ops/kill-switch');
    expectProblem(read, 403, 'FORBIDDEN');

    const off = await callApi(page, 'POST', '/api/ops/kill-switch', { enabled: false });
    expectProblem(off, 403, 'FORBIDDEN');
    expect(await readAiSwitchAsOps(browser)).toBe(true);

    // The other direction: a role that may not change the switch cannot turn the AI back on either.
    await setAiSwitchAsOps(browser, false);
    const on = await callApi(page, 'POST', '/api/ops/kill-switch', { enabled: true });
    expectProblem(on, 403, 'FORBIDDEN');
    expect(await readAiSwitchAsOps(browser)).toBe(false);
  });
}
