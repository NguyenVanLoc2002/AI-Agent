import { request as httpRequest } from 'node:http';
import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { FAKE_PROVIDER_URL } from './env';
import {
  callApi, customerChat, ensureAiOn, fakeProviderCalls, inboxLoaded, reachedProvider, rememberedConversation, resolveHeld,
  sendAsCustomer, setAiSwitchAsOps, shortConversation, signInWithForm, withContext,
} from './support';

// FE-E2E-FLOWS (A-72) · the main screens through the real backend, with the Fake provider of
// e2e/fakeProvider.mjs behind the gateway: F1 customer chat with a quote and its confirmation (UC-01, UC-03,
// UC-04), F2/F3 staff inbox and held conversation (UC-05, UC-06), the AI switch off for a chat (UC-08), and F4
// Security Inspector (UC-07). The tests share what they create, so they run in order. New conversations per full
// run (decision 5A, limit 5 per customer per hour): cust-a 2 (roles.spec.ts, F1), cust-c 2 (F2/F3, AI off).
//
// Synthetic data only: OE 04465-0K290 is a Mock ERP demo product. Not SEC-01..07 evidence: the egress checks
// look for known values after the fact; the backend's own egress policy is what blocks.

test.describe.configure({ mode: 'serial' });

const OE = '04465-0K290';
const STAFF_REPLY = 'Chào anh, em là nhân viên tư vấn của cửa hàng.';
const THANKS = 'Cảm ơn';
const AI_DEFAULT = 'Bạn cần tra cứu phụ tùng nào?';
const WAITING = 'Đang chờ nhân viên tư vấn';
const STAFF_JOINED = 'Nhân viên tư vấn đã tham gia';

/** What the flows created and showed; F4 and the egress checks use it. */
const seen = {
  startedAt: new Date(0),
  providerWired: false,
  quoteRef: '',
  confirmationToken: '',
  amounts: [] as string[],
  conversations: [] as string[],
};

test.beforeAll(async ({ browser }) => {
  await ensureAiOn(browser);
  seen.startedAt = new Date(Date.now() - 1_000);
});
test.afterEach(async ({ browser }) => { await ensureAiOn(browser); });

/** An amount as plain digits and grouped by ".", ",", a space and a no-break space (review security M2). */
function amountForms(digits: string): string[] {
  const grouped = (separator: string) => digits.replace(/\B(?=(\d{3})+(?!\d))/g, separator);
  return [...new Set([digits, grouped('.'), grouped(','), grouped(' '), grouped(' ')])].filter((v) => v.length >= 4);
}

/** Amounts of the quote card (unit prices, line totals, total), in every form above. */
async function cardAmounts(page: Page): Promise<string[]> {
  const card = page.getByRole('region', { name: /^Báo giá / });
  const cells = await card.locator('tbody td:nth-child(2), tbody td:nth-child(3), tfoot td').allInnerTexts();
  const amounts = new Set<string>();
  for (const cell of cells) {
    const digits = (cell.split('×').pop() ?? '').replace(/\D/g, '');
    for (const form of amountForms(digits)) amounts.add(form);
  }
  return [...amounts];
}

/** Values that must never reach the model (rule 10): customer-visible references, amounts, IDs, user names. */
function restrictedValues(): string[] {
  return [seen.quoteRef, seen.confirmationToken, ...seen.amounts, ...seen.conversations, 'cust-a', 'cust-c'].filter((v) => v.length >= 4);
}

/** Raw HTTP to the Fake provider, to send headers fetch would not (Host). Answers the status only. */
function rawStatus(path: string, headers: Record<string, string>, body?: string): Promise<number> {
  const url = new URL(path, FAKE_PROVIDER_URL);
  return new Promise((resolve, reject) => {
    const call = httpRequest({ host: url.hostname, port: url.port, path: url.pathname, method: body ? 'POST' : 'GET', headers }, (response) => {
      response.resume();
      resolve(response.statusCode ?? 0);
    });
    call.on('error', reject);
    call.end(body);
  });
}

test('the Fake provider refuses browsers, other hosts and calls without the run token', async () => {
  const host = new URL(FAKE_PROVIDER_URL).host;
  const token = process.env.APAS_E2E_FAKE_TOKEN ?? '';
  expect(await rawStatus('/__e2e/calls', { Host: host, 'X-E2E-Token': token })).toBe(200);
  expect(await rawStatus('/__e2e/calls', { Host: host })).toBe(403);
  expect(await rawStatus('/__e2e/calls', { Host: 'evil.example', 'X-E2E-Token': token })).toBe(400);
  expect(await rawStatus('/__e2e/calls', { Host: host, Origin: 'http://evil.example', 'X-E2E-Token': token })).toBe(400);
  expect(await rawStatus('/v1/fake-provider/complete', { Host: host, 'Content-Type': 'text/plain' }, '{}')).toBe(400);
});

test('F1: a customer gets a quote from the chat, keeps it after a reload and confirms it; prices never reach the model', async ({ page }) => {
  await customerChat(page, 'cust-a');
  const before = await fakeProviderCalls();

  const turn = page.waitForResponse((r) => r.request().method() === 'POST' && /\/api\/demo\/conversations\/[^/]+\/messages$/.test(r.url()));
  await sendAsCustomer(page, `Báo giá 2 bộ má phanh ${OE}`);
  const card = page.getByRole('region', { name: /^Báo giá / });
  await expect(card).toBeVisible({ timeout: 30_000 });
  // The answer comes after the whole turn, so all three calls are counted: search, quote, answer.
  expect(await fakeProviderCalls() - before, 'model calls for one quote turn (is the backend started with APAS_GATEWAY_PROVIDER_BASE_URL?)').toBe(3);
  seen.providerWired = true;
  const answer = (await (await turn).json()) as { quoteCard?: { confirmationToken?: unknown } };
  seen.confirmationToken = typeof answer.quoteCard?.confirmationToken === 'string' ? answer.quoteCard.confirmationToken : '';

  await expect(page.getByRole('list', { name: 'Tin nhắn' }).getByText('Báo giá đã được gửi bên dưới.')).toBeVisible();
  await expect(card.getByText('Chờ xác nhận', { exact: true })).toBeVisible();
  await expect(card.getByText(/^Phiên bản 1 · Hiệu lực đến /)).toBeVisible();
  await expect(card.getByRole('rowheader', { name: 'Tạm tính' })).toBeVisible();
  await expect(card.getByText(/^2 × /)).toBeVisible();

  seen.quoteRef = ((await card.getAttribute('aria-label')) ?? '').replace(/^Báo giá /, '');
  seen.amounts = await cardAmounts(page);
  seen.conversations.push(await rememberedConversation(page));
  expect(seen.quoteRef).toMatch(/^BG-[A-Z0-9]{6,}$/);
  expect(seen.confirmationToken.length).toBeGreaterThanOrEqual(8);
  expect(seen.amounts.length).toBeGreaterThan(0);

  // A second turn sends the history again (review security M2: a replay must not carry the quote back).
  const beforeSecond = await fakeProviderCalls();
  await sendAsCustomer(page, THANKS);
  await expect(page.getByRole('list', { name: 'Tin nhắn' }).getByText(AI_DEFAULT).last()).toBeVisible();
  expect(await fakeProviderCalls() - beforeSecond).toBe(1);

  // Egress (rule 10, decision 3A): the model saw a quote handle, never the customer's references or amounts.
  for (const value of restrictedValues()) {
    expect(await reachedProvider(value), 'a quote reference, token, amount, conversation or user name reached the model').toBe(false);
  }
  // Controls: the check sees what the model did receive, also in the second turn.
  expect(await reachedProvider(OE)).toBe(true);
  expect(await reachedProvider(THANKS)).toBe(true);

  // The tab remembers the quote; a reload reads it again from the backend.
  await page.reload();
  await expect(page.getByRole('region', { name: `Báo giá ${seen.quoteRef}` })).toBeVisible();

  await page.getByRole('button', { name: 'Xác nhận báo giá' }).click();
  const confirmed = page.getByRole('region', { name: `Báo giá ${seen.quoteRef}` });
  // Both the status badge and the result notice say so.
  await expect(confirmed.getByText('Đã gửi yêu cầu đặt hàng', { exact: true }).first()).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('button', { name: 'Xác nhận báo giá' })).toHaveCount(0);
});

test('F2/F3: a handoff reaches the right team, staff reply and hand the conversation back to the AI', async ({ page, browser }) => {
  expect(seen.providerWired, 'F1 must show first that the backend calls the Fake provider').toBe(true);
  await customerChat(page, 'cust-c');
  const before = await fakeProviderCalls();
  await sendAsCustomer(page, 'Cho tôi gặp nhân viên tư vấn');
  await expect(page.getByText(WAITING).first()).toBeVisible({ timeout: 30_000 });
  // The model asked for the handoff (one call: the turn ends with the requestHandoff proposal), so it is not a
  // keyword rule of the backend.
  expect(await fakeProviderCalls() - before).toBe(1);
  const conversation = await rememberedConversation(page);
  seen.conversations.push(conversation);
  const code = shortConversation(conversation);

  // Another team does not see it and cannot read it.
  await withContext(browser, async (other) => {
    await signInWithForm(other, 'sales-2');
    await inboxLoaded(other);
    await expect(other.getByRole('button', { name: `Nhận xử lý cuộc trò chuyện ${code}` })).toHaveCount(0);
    expect([403, 404]).toContain((await callApi(other, 'GET', `/api/internal/conversations/${conversation}/messages`)).status);
  });

  await withContext(browser, async (staff) => {
    await signInWithForm(staff, 'sales-1');
    await inboxLoaded(staff);
    await staff.getByRole('button', { name: `Nhận xử lý cuộc trò chuyện ${code}` }).click();
    await staff.getByRole('button', { name: `Mở cuộc trò chuyện ${code}` }).click();
    await expect(staff.getByRole('heading', { name: `Cuộc trò chuyện ${code}`, level: 1 })).toBeVisible();
    await expect(staff.getByRole('list', { name: 'Tin nhắn' }).getByText('Cho tôi gặp nhân viên tư vấn')).toBeVisible();
    // Control for the denials: the same URL is readable by the member who holds it.
    expect((await callApi(staff, 'GET', `/api/internal/conversations/${conversation}/messages`)).status).toBe(200);
    await staff.getByRole('textbox', { name: 'Tin nhắn cho khách' }).fill(STAFF_REPLY);
    await staff.getByRole('button', { name: 'Gửi cho khách' }).click();
    await expect(staff.getByText('Đã gửi tin cho khách.')).toBeVisible();

    // The customer screen reads the staff reply (every 5 s while staff handle the conversation, A-68).
    await page.bringToFront();
    await expect(page.getByRole('list', { name: 'Tin nhắn' }).getByText(STAFF_REPLY)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(STAFF_JOINED)).toBeVisible();

    // Resolve: the staff access ends, for a read and for a write.
    await staff.bringToFront();
    await resolveHeld(staff);
    expect([403, 404]).toContain((await callApi(staff, 'GET', `/api/internal/conversations/${conversation}/messages`)).status);
    expect([403, 404]).toContain((await callApi(staff, 'POST', `/api/internal/conversations/${conversation}/messages`, { clientEventId: crypto.randomUUID(), text: 'Tin sau khi hoàn tất' })).status);
  });

  // The AI answers the customer again (review QA 11).
  await page.bringToFront();
  await expect(page.getByText(STAFF_JOINED)).toHaveCount(0, { timeout: 20_000 });
  await expect(page.getByText(WAITING)).toHaveCount(0);
  const beforeAgain = await fakeProviderCalls();
  await sendAsCustomer(page, THANKS);
  await expect(page.getByRole('list', { name: 'Tin nhắn' }).getByText(AI_DEFAULT).last()).toBeVisible();
  expect(await fakeProviderCalls() - beforeAgain).toBe(1);

  // Another customer cannot read cust-a's conversation or quote, nor confirm it (a SEC-02-like case, not SEC-02 evidence).
  expect([403, 404]).toContain((await callApi(page, 'GET', `/api/demo/conversations/${seen.conversations[0]}/messages`)).status);
  expect([403, 404]).toContain((await callApi(page, 'GET', `/api/demo/quotes/${seen.quoteRef}`)).status);
  const foreignConfirm = await callApi(page, 'POST', `/api/demo/quotes/${seen.quoteRef}/confirm`, { version: 1, confirmationToken: seen.confirmationToken });
  expect([403, 404, 409, 410]).toContain(foreignConfirm.status);
});

test('AI off: a customer message goes to the staff inbox and the model is never called', async ({ page, browser }) => {
  expect(seen.providerWired, 'F1 must show first that the backend calls the Fake provider').toBe(true);
  await customerChat(page, 'cust-c');
  await setAiSwitchAsOps(browser, false);
  const before = await fakeProviderCalls();

  await sendAsCustomer(page, `Báo giá ${OE}`);
  await expect(page.getByText(WAITING).first()).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('region', { name: /^Báo giá / })).toHaveCount(0);
  // No call now, nor a few seconds later (review security M3).
  await page.waitForTimeout(3_000);
  expect(await fakeProviderCalls(), 'model calls while the AI is off').toBe(before);
  const conversation = await rememberedConversation(page);
  seen.conversations.push(conversation);
  const code = shortConversation(conversation);
  await ensureAiOn(browser);

  // It reached the team's inbox with the AI-off reason; staff claim and resolve it, so no request is left open
  // (review security L2).
  await withContext(browser, async (staff) => {
    await signInWithForm(staff, 'sales-1');
    await inboxLoaded(staff);
    const claim = staff.getByRole('button', { name: `Nhận xử lý cuộc trò chuyện ${code}` });
    await expect(staff.getByRole('row').filter({ has: claim }).getByText('Trợ lý AI đang tắt')).toBeVisible();
    await claim.click();
    await staff.getByRole('button', { name: `Mở cuộc trò chuyện ${code}` }).click();
    await resolveHeld(staff);
  });
});

/** UTC "dd/mm/yyyy hh:mm:ss" of the inspector table (timezoneId UTC in playwright.config.ts). */
function parseShown(value: string): Date {
  const match = /(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2}):(\d{2})/.exec(value);
  if (!match) throw new Error('unreadable time in the events table');
  const [, d, m, y, hh, mm, ss] = match;
  return new Date(Date.UTC(Number(y), Number(m) - 1, Number(d), Number(hh), Number(mm), Number(ss)));
}

test('F4: the security reviewer sees this run\'s quote confirmation and its trace, without customer data', async ({ page }) => {
  // Review QA 4: without the values of the earlier flows the checks below would pass trivially.
  expect(seen.quoteRef !== '' && seen.amounts.length > 0 && seen.conversations.length === 3, 'F1 to the AI-off flow must run first').toBe(true);

  // Every inspector answer the browser receives is checked too, not only what the page shows (review security M4).
  const answers: Promise<string>[] = [];
  page.on('response', (response) => {
    if (response.url().includes('/api/demo/security/')) answers.push(response.text().catch(() => ''));
  });

  await signInWithForm(page, 'secrev-1');
  await expect(page.getByRole('heading', { name: 'Sự kiện bảo mật', level: 1 })).toBeVisible();
  await page.getByLabel('Loại sự kiện').selectOption({ value: 'QUOTE_CONFIRMATION' });
  await page.getByRole('button', { name: 'Áp dụng' }).click();
  await expect(page.getByText('Bộ lọc đã đổi, chưa áp dụng.')).toHaveCount(0);

  // Review QA 2/3: only confirmations are listed, and the newest is this run's (F1), not an older one.
  const rows = page.getByRole('table').first().locator('tbody tr');
  await expect(rows.first().getByText('QUOTE_CONFIRMATION')).toBeVisible();
  expect(await rows.filter({ hasNotText: 'QUOTE_CONFIRMATION' }).count()).toBe(0);
  const row = rows.first();
  const shownAt = parseShown(await row.locator('td').first().innerText()).getTime();
  expect(shownAt).toBeGreaterThanOrEqual(Math.floor(seen.startedAt.getTime() / 1000) * 1000);

  // Review QA 1: wait for the trace itself, not the panel heading that is always there.
  await row.getByRole('button', { name: /^Xem trace / }).first().click();
  await expect(page.getByRole('heading', { name: /^Trace / })).toBeVisible();
  await expect(page.getByText('Chưa tải được trace')).toHaveCount(0);
  await expect(page.getByText('Không tìm thấy trace')).toHaveCount(0);

  const html = await page.content();
  const bodies = (await Promise.all(answers)).join('\n');
  expect(bodies.length).toBeGreaterThan(0);
  for (const value of restrictedValues()) {
    expect(html.includes(value), 'the inspector page shows a quote, token, amount, conversation or user name').toBe(false);
    expect(bodies.includes(value), 'an inspector answer carries a quote, token, amount, conversation or user name').toBe(false);
  }
});
