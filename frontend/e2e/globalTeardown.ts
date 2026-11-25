import { chromium } from '@playwright/test';
import { ensureAiOn } from './support';

// FE-E2E (A-71), review security M4 / QA M6: a last attempt to leave the shared AI switch on, also after a test
// or its clean-up failed. Runs before Playwright stops the dev server. On failure it says so in one line, with no
// server answer and no credential, and fails the run.

export default async function globalTeardown(): Promise<void> {
  const browser = await chromium.launch();
  try {
    await ensureAiOn(browser);
  } catch {
    throw new Error('FE-E2E: the AI switch could not be restored. Sign in as OPS and turn the AI on by hand (screen "Công tắc AI").');
  } finally {
    await browser.close();
  }
}
