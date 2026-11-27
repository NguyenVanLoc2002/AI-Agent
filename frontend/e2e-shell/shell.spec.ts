import { expect, test, type Page } from '@playwright/test';

// FE-UI-P2 (P2-T14): the shell in a real browser against the dev mock (playwright.shell.config.ts). Synthetic mock
// users only ("sales", "ops", "cust" with the mock password of src/mocks/backend.ts); no real credential.

const MOCK_PASSWORD = 'mock';

async function signIn(page: Page, user: 'sales' | 'ops' | 'secrev' | 'cust', hash = '') {
  await page.goto(`/${hash}`);
  await page.getByLabel(/Tài khoản/).fill(user);
  await page.getByLabel(/Mật khẩu/).fill(MOCK_PASSWORD);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await expect(page.getByRole('button', { name: 'Đăng xuất' })).toBeVisible();
}

async function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
}

for (const width of [1440, 1280]) {
  test(`staff at ${width} px: sidebar open, page box as the F artboards, no horizontal page scroll`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await signIn(page, 'sales');

    await expect(page.getByRole('heading', { level: 1, name: 'Hộp chờ nhân viên' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Thu gọn thanh bên' })).toBeVisible();
    const box = await page.locator('.ap-page').evaluate((el) => {
      const s = getComputedStyle(el);
      return { top: s.paddingTop, left: s.paddingLeft, gap: s.rowGap, max: s.maxWidth };
    });
    expect(box).toEqual({ top: '28px', left: '32px', gap: '20px', max: '1192px' });
    const nav = await page.getByRole('navigation', { name: 'Điều hướng chính' }).boundingBox();
    expect(nav?.width).toBe(248);
    expect(await horizontalOverflow(page)).toBe(0);
  });
}

for (const width of [1279, 1024]) {
  test(`staff at ${width} px: sidebar starts collapsed and opens on request`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await signIn(page, 'sales');

    const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
    expect((await nav.boundingBox())?.width).toBe(64);
    await expect(nav.getByText('Kinh doanh')).toHaveCount(0);
    await page.getByRole('button', { name: 'Mở rộng thanh bên' }).click();
    await expect(nav.getByText('Kinh doanh')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Thu gọn thanh bên' })).toBeVisible();
    expect(await horizontalOverflow(page)).toBe(0);
  });
}

test('customer at 390 px: no sidebar and no horizontal page scroll', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page, 'cust');

  await expect(page).toHaveURL(/#\/chat$/);
  await expect(page.getByRole('navigation', { name: 'Điều hướng chính' })).toHaveCount(0);
  expect(await horizontalOverflow(page)).toBe(0);
});

test('a deep link to another role\'s screen shows "no access" inside the own shell', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await signIn(page, 'sales', '#/cong-tac-ai');

  await expect(page.getByText('Bạn không có quyền xem trang này')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Công tắc trợ lý AI' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Về trang chính' }).click();
  await expect(page).toHaveURL(/#\/hop-cho$/);
});

test('the skip link appears on keyboard focus and moves focus to the main area', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await signIn(page, 'ops');
  await expect(page.getByRole('heading', { level: 1, name: 'Công tắc trợ lý AI' })).toBeVisible();

  // After sign-in focus is on the page heading (not lost on <body>).
  await expect(page.getByRole('heading', { level: 1, name: 'Công tắc trợ lý AI' })).toBeFocused();
  const skip = page.getByRole('link', { name: 'Bỏ qua tới nội dung chính' });
  expect((await skip.boundingBox())?.y ?? 0).toBeLessThan(0);
  // Walk back to the start of the page: the skip link is the first stop.
  for (let i = 0; i < 12 && !(await skip.evaluate((el) => el === document.activeElement)); i += 1) {
    await page.keyboard.press('Shift+Tab');
  }
  await expect(skip).toBeFocused();
  const shown = await skip.boundingBox();
  expect(shown?.y ?? -1).toBeGreaterThanOrEqual(0);
  await page.keyboard.press('Enter');
  await expect(page.locator('#noi-dung-chinh')).toBeFocused();
  await expect(page).toHaveURL(/#\/cong-tac-ai$/);
});

test('in forced colours a focused design-system control still has a visible outline', async ({ page }) => {
  await page.emulateMedia({ forcedColors: 'active' });
  await page.setViewportSize({ width: 1440, height: 900 });
  await signIn(page, 'sales');

  const toggle = page.getByRole('button', { name: 'Thu gọn thanh bên' });
  await toggle.focus();
  await page.keyboard.press('Shift+Tab');
  await page.keyboard.press('Tab');
  const outline = await toggle.evaluate((el) => {
    const s = getComputedStyle(el);
    return { style: s.outlineStyle, width: parseFloat(s.outlineWidth) };
  });
  expect(outline.style).toBe('solid');
  expect(outline.width).toBeGreaterThan(0);
});
