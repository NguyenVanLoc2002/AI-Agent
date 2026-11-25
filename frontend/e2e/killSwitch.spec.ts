import { expect, test } from './fixtures';
import { ensureAiOn, readAiSwitchAsOps, signInWithForm } from './support';

// FE-E2E (A-71) · F5 · OP-01 AI kill switch (UC-08, FR-012) through the real backend. OPS signs in with the
// form, turns the AI off through the danger dialog, sees the state survive a page reload (read from the
// server), and turns it on again. The state is checked independently by a second OPS session.

test.beforeEach(async ({ browser }) => { await ensureAiOn(browser); });
test.afterEach(async ({ browser }) => { await ensureAiOn(browser); });

test('OPS turns the AI off with the confirmation and on again', async ({ page, browser }) => {
  await signInWithForm(page, 'ops-1');

  await expect(page.getByRole('heading', { name: 'Công tắc trợ lý AI', level: 1 })).toBeVisible();
  // The same locator roles.spec.ts expects to find nothing for the other roles.
  await expect(page.getByRole('navigation').getByRole('button', { name: 'Công tắc AI' })).toBeVisible();
  await expect(page.getByText('AI đang bật', { exact: true })).toBeVisible();
  const toggle = page.getByRole('switch', { name: 'Trợ lý AI' });
  await expect(toggle).toHaveAttribute('aria-checked', 'true');

  // "Hủy" keeps the AI on: nothing is sent.
  await toggle.click();
  const dialog = page.getByRole('alertdialog', { name: 'Tắt trợ lý AI?' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Hủy' }).click();
  await expect(dialog).toBeHidden();
  await expect(toggle).toHaveAttribute('aria-checked', 'true');
  expect(await readAiSwitchAsOps(browser)).toBe(true);

  // Confirmed: the backend stores the change.
  await toggle.click();
  await page.getByRole('alertdialog', { name: 'Tắt trợ lý AI?' }).getByRole('button', { name: 'Tắt AI' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã tắt trợ lý AI.' })).toBeVisible();
  await expect(page.getByText('AI đang tắt', { exact: true })).toBeVisible();
  await expect(page.getByText('Do người vận hành tắt')).toBeVisible();
  await expect(toggle).toHaveAttribute('aria-checked', 'false');
  expect(await readAiSwitchAsOps(browser)).toBe(false);

  // A reload reads the state from the server again, with the same session.
  await page.reload();
  await expect(page.getByText('AI đang tắt', { exact: true })).toBeVisible();

  // Turning it on needs no confirmation.
  await page.getByRole('switch', { name: 'Trợ lý AI' }).click();
  await expect(page.getByRole('alertdialog')).toHaveCount(0);
  await expect(page.getByRole('status').filter({ hasText: 'Đã bật lại trợ lý AI.' })).toBeVisible();
  await expect(page.getByText('AI đang bật', { exact: true })).toBeVisible();
  expect(await readAiSwitchAsOps(browser)).toBe(true);
});
