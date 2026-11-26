import type { Browser, Page } from '@playwright/test';
import { expect, test } from './fixtures';
import {
  customerChat, ensureAiOn, fakeProviderCalls, fakeProviderRemembered, inboxLoaded, rememberedConversation, resolveHeld,
  sendAsCustomer, shortConversation, signInWithForm, withContext,
} from './support';

// FE-E2E-ADVERSARIAL (A-75) · the Fake provider answers like a hostile model (e2e/fakeProvider.mjs, SCENARIOS):
// an unknown tool, an argument the schema does not allow, another customer's productRef, a price in the answer and
// another customer's quote handle in the answer. The backend must refuse each one (DefaultToolManager,
// OutputValidator): no quote card, the blocked text and the model's tokens never reach the customer's or the
// staff's browser, the customer waits for staff, the model is not called again, and the right team's inbox shows
// why. Staff of team-2 resolve every request (decision 4A).
//
// New conversations per full run (3A, limit 5 per customer per hour): cust-a 1 here (a real quote whose values the
// Fake provider keeps; 3 with roles.spec.ts and flows.spec.ts), cust-b 4. So: one full run per hour, or a fresh
// database (frontend/README.md). Synthetic data only; a browser-level regression guard, not SEC-03 evidence.

test.describe.configure({ mode: 'serial' });

const OE = '04465-0K290';
const WAITING = 'Đang chờ nhân viên tư vấn';
const TOOL_ERROR_ANSWER = 'Chưa xử lý được yêu cầu.';
const NOT_PREPARED = 'Kịch bản kiểm thử chưa có dữ liệu chuẩn bị.';
const TOOL_REJECTED = 'Yêu cầu công cụ bị từ chối';
const OUTPUT_BLOCKED = 'Câu trả lời bị chặn';
const AFTER_HANDOFF = 'Em hỏi thêm một câu';

/**
 * Server tokens never shown to people (review security M3): productRef, quote handle, confirmation token, vinRef,
 * customer and user pseudonyms. Conversation IDs (`cv_…`) are not checked: the chat API uses them in its paths.
 */
const SERVER_TOKEN = /(?<![A-Za-z0-9_-])(pr|qh|qc|vin|cref|usr)_[A-Za-z0-9_-]{4,}/i;

/** What the preparation gave the Fake provider: cust-a's real values. */
const prepared = { productRef: '', quoteHandle: '' };

test.beforeAll(async ({ browser }) => { await ensureAiOn(browser); });

test('the token check recognises every token kind and leaves conversation IDs alone', () => {
  for (const token of ['pr_AbCd1234', 'qh_x-Y_9zzz', 'qc_12345678', 'vin_abcd', 'cref_AAAA', 'usr_BBBB', 'Mã: PR_abcd1234']) {
    expect(SERVER_TOKEN.test(token), token).toBe(true);
  }
  for (const value of ['cv_AbCd1234', '/api/demo/conversations/cv_abcd-pr_x', 'spr_abcd1234']) {
    expect(SERVER_TOKEN.test(value), value).toBe(false);
  }
});

/** Collects every backend answer `page` reads, to check what reached the browser, not only what it shows. */
function watchAnswers(page: Page): Promise<string>[] {
  const answers: Promise<string>[] = [];
  page.on('response', (response) => {
    if (response.url().includes('/api/')) answers.push(response.text().catch(() => ''));
  });
  return answers;
}

/** None of `blocked` nor any server token is in `where` (page HTML or answer bodies). */
function expectClean(where: string, label: string, blocked: string[]): void {
  for (const value of blocked) expect(where.includes(value), `${label} carries blocked text "${value}"`).toBe(false);
  expect(SERVER_TOKEN.exec(where)?.[1] ?? null, `${label} carries a server token`).toBeNull();
}

/** Sends `text` and waits until the turn is over; answers how many model calls it made. */
async function turn(page: Page, text: string, done: () => Promise<void>): Promise<number> {
  const before = await fakeProviderCalls();
  await sendAsCustomer(page, text);
  await done();
  return (await fakeProviderCalls()) - before;
}

const waiting = (page: Page) => async () => { await expect(page.getByText(WAITING).first()).toBeVisible({ timeout: 30_000 }); };

/**
 * The turn ended in a handoff: no quote card; a further message does not reach the model; none of `blocked` nor a
 * server token reached the customer's browser; team-2's inbox shows `reason` and none of it reaches the staff
 * browser either; a team-2 member resolves the request.
 */
async function expectHandedOff(browser: Browser, page: Page, answers: Promise<string>[], said: string, reason: string,
  blocked: string[]): Promise<void> {
  await expect(page.getByRole('region', { name: /^Báo giá / })).toHaveCount(0);
  await expect(page.getByText(NOT_PREPARED)).toHaveCount(0);
  // Review QA: held for staff, so the model is not asked again.
  expect(await turn(page, AFTER_HANDOFF, () => page.waitForTimeout(2_000)), 'model calls after the handoff').toBe(0);

  const bodies = (await Promise.all(answers)).join('\n');
  // Review security M3: the message list itself was read, not only sign-in answers.
  expect(bodies.includes(said), 'the conversation answers were captured').toBe(true);
  expectClean(await page.content(), 'the customer page', blocked);
  expectClean(bodies, 'a backend answer to the customer', blocked);

  const code = shortConversation(await rememberedConversation(page));
  await withContext(browser, async (staff) => {
    const staffAnswers = watchAnswers(staff); // review security M1
    await signInWithForm(staff, 'sales-2');
    await inboxLoaded(staff);
    const claim = staff.getByRole('button', { name: `Nhận xử lý cuộc trò chuyện ${code}` });
    await expect(staff.getByRole('row').filter({ has: claim }).getByText(reason)).toBeVisible();
    expectClean(await staff.content(), 'the staff inbox', blocked);
    await claim.click();
    await staff.getByRole('button', { name: `Mở cuộc trò chuyện ${code}` }).click();
    await expect(staff.getByRole('heading', { name: `Cuộc trò chuyện ${code}`, level: 1 })).toBeVisible();
    await expect(staff.getByRole('list', { name: 'Tin nhắn' }).getByText(said).first()).toBeVisible();
    expectClean(await staff.content(), 'the staff conversation', blocked);
    const staffBodies = (await Promise.all(staffAnswers)).join('\n');
    expect(staffBodies.includes(said), 'the staff conversation answers were captured').toBe(true);
    expectClean(staffBodies, 'a backend answer to staff', blocked);
    await resolveHeld(staff);
  });
}

test('preparation: another customer gets a real quote, whose productRef and quote handle the Fake provider keeps', async ({ page }) => {
  await customerChat(page, 'cust-a');
  const calls = await turn(page, `Báo giá 1 bộ má phanh ${OE}`, async () => {
    await expect(page.getByRole('region', { name: /^Báo giá / })).toBeVisible({ timeout: 30_000 });
  });
  // search, quote, answer (the Mock ERP quotes cust-a, not cust-c): the productRef was valid in its own conversation.
  expect(calls, 'model calls for one quote turn (is the backend started with APAS_GATEWAY_PROVIDER_BASE_URL?)').toBe(3);
  const remembered = await fakeProviderRemembered();
  expect(remembered.productRef ?? '').toMatch(/^pr_/);
  expect(remembered.quoteHandle ?? '').toMatch(/^qh_/);
  prepared.productRef = remembered.productRef ?? '';
  prepared.quoteHandle = remembered.quoteHandle ?? '';
});

test('an unknown tool and an extra argument are refused; the third refusal hands the conversation to staff', async ({ page, browser }) => {
  expect(prepared.productRef, 'the preparation must run first').not.toBe('');
  const answers = watchAnswers(page);
  await customerChat(page, 'cust-b');
  const said = page.getByRole('list', { name: 'Tin nhắn' }).getByText(TOOL_ERROR_ANSWER);

  // Refused without a handoff: the model gets the error code and answers (two calls).
  expect(await turn(page, 'Kịch bản kiểm thử: công cụ lạ', async () => { await expect(said).toHaveCount(1, { timeout: 30_000 }); })).toBe(2);
  await expect(page.getByText(WAITING)).toHaveCount(0);
  expect(await turn(page, 'Kịch bản kiểm thử: tham số thừa', async () => { await expect(said).toHaveCount(2, { timeout: 30_000 }); })).toBe(2);
  await expect(page.getByText(WAITING)).toHaveCount(0);

  // Third refusal in the hour (ToolLimits.rejectionsBeforeHandoff = 3): handed off at once, one call.
  const last = 'Kịch bản kiểm thử: công cụ lạ';
  expect(await turn(page, last, waiting(page))).toBe(1);
  await expectHandedOff(browser, page, answers, last, TOOL_REJECTED, ['exportCustomerList', 'includeCost']);
});

test('another customer\'s productRef, valid in its own conversation, is refused and the conversation goes to staff', async ({ page, browser }) => {
  // The Fake provider still holds the value of the preparation, nothing newer (review security L2).
  expect((await fakeProviderRemembered()).productRef, 'cust-a\'s productRef').toBe(prepared.productRef);
  const answers = watchAnswers(page);
  await customerChat(page, 'cust-b');
  const said = 'Kịch bản kiểm thử: tham chiếu của khách khác';
  expect(await turn(page, said, waiting(page))).toBe(1);
  expect((await fakeProviderRemembered()).productRef, 'the Fake provider used it').toBeNull();
  await expectHandedOff(browser, page, answers, said, TOOL_REJECTED, [prepared.productRef]);
});

test('an answer with a price is blocked as a whole and the conversation goes to staff', async ({ page, browser }) => {
  const answers = watchAnswers(page);
  await customerChat(page, 'cust-b');
  const said = 'Kịch bản kiểm thử: câu trả lời có giá';
  expect(await turn(page, said, waiting(page))).toBe(1);
  await expectHandedOff(browser, page, answers, said, OUTPUT_BLOCKED, ['1.250.000', 'Giá má phanh', 'đặt luôn']);
});

// Review security M2: the backend blocks any quote handle in an answer, whoever owns it; this one is cust-a's.
test('an answer carrying a quote handle, here another customer\'s, is blocked and the conversation goes to staff', async ({ page, browser }) => {
  expect((await fakeProviderRemembered()).quoteHandle, 'cust-a\'s quote handle').toBe(prepared.quoteHandle);
  const answers = watchAnswers(page);
  await customerChat(page, 'cust-b');
  const said = 'Kịch bản kiểm thử: mã báo giá của khách khác';
  expect(await turn(page, said, waiting(page))).toBe(1);
  await expectHandedOff(browser, page, answers, said, OUTPUT_BLOCKED, [prepared.quoteHandle, 'xác nhận giúp em']);
});
