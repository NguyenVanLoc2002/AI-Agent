// FE-REACT19 (owner decision 2026-11-26, option B of the P2 review security M3): React writes every error caught by an
// error boundary, every uncaught render error and every recoverable error to the browser console, with its message and
// stack. A message can carry data (rule 10: logs record event IDs and codes, never content), so the root replaces
// those reporters. The screen still shows the boundary's fixed text (ShellPages.tsx). Nothing of the error is logged
// or sent: only a fixed line, so a developer knows a render error happened and where to look.
//
// These options are given to createRoot (main.tsx) and to the test renderer (states.test.tsx).

export const WITHHELD_NOTICE = '[APAS] Lỗi hiển thị đã được chặn; chi tiết không được ghi (rule 10).';

const report = () => {
  console.warn(WITHHELD_NOTICE);
};

export const ROOT_ERROR_OPTIONS = {
  onCaughtError: report,
  onUncaughtError: report,
  onRecoverableError: report,
} as const;
