# FE-REACT19 · Nâng React 19 để chặn React ghi lỗi render ra console — bằng chứng

> 2026-11-26 · quyết định chủ dự án: "chọn B" (câu hỏi sau review security P2 M3) · FE-UI-D21 · A-85 · worktree `D:/Project_Private/AI-Agent-fe`, nhánh `feat/FE-REACT19-upgrade` từ `main` `29840af` (PR #68).
> Một lát riêng, làm trước P3 để P3 xây trên phiên bản cuối.

## 1. Thay đổi
| Gói | Trước | Sau | Ghi chú |
|---|---|---|---|
| `react`, `react-dom` | 18.3.1 | **19.3.1** (dist-tag `latest`, phát hành 2026-10-14) | ghim chính xác |
| `@types/react`, `@types/react-dom` | 18.3.32 / 18.3.7 | **19.3.0** (2026-09-09) | 19.3.3 mới 1 ngày nên không dùng |
- Tương thích đã kiểm: `@testing-library/react` 16.3.3 (peer `^18 || ^19`), `react-router-dom` 7.18.5 (peer `>=18`); mã không dùng API bị gỡ ở React 19 (`forwardRef`, `defaultProps`, `propTypes`, `findDOMNode`, `ReactDOM.render`); typecheck 0 lỗi không cần sửa kiểu; bản sao design system không đổi.
- `src/app/rootErrors.ts`: `onCaughtError`, `onUncaughtError`, `onRecoverableError` chỉ ghi một dòng cố định `[APAS] Lỗi hiển thị đã được chặn; chi tiết không được ghi (rule 10).`; `src/main.tsx` tạo root với các option đó. Màn hình vẫn hiện chữ cố định của boundary (P2).

## 2. Gate (2026-11-26T09:38Z, sửa test đường dẫn sau đó, chạy lại)
| Lệnh | Kết quả |
|---|---|
| `npm run typecheck` | exit 0 |
| `npx vitest run` | exit 0 · 19 file · **298** passed (294 + 4 mới), hai lần liên tiếp |
| `npm run build` (+ verify-no-mocks) | exit 0 · JS **399,72 kB** (gzip 119,09; trước 320,50 / 96,28) |
| `npm run ds:verify` | exit 0 · 73 |
| `npm audit` | 0 |
| `npm run e2e:shell` (Chromium thật, React 19 dev + bản build dưới CSP bắt buộc) | exit 0 · 9 passed, 0 vi phạm CSP |
| `git diff --check` | exit 0 |

## 3. Test mới
- `src/app/rootErrors.test.tsx`: đối chứng — không có option thì React ghi honeytoken ra console; có option thì lỗi được boundary bắt không lộ honeytoken/tên component ở `console.error/warn/log/info`, chỉ có dòng cố định; lỗi không được bắt cũng không lộ honeytoken.
- `config/mainEntry.test.ts`: `main.tsx` tạo đúng một root với `ROOT_ERROR_OPTIONS`.
- Mutation: bỏ `onCaughtError` → test đỏ.

## 4. Giới hạn
- Nhánh lỗi **không được bắt** trong trình duyệt: trong jsdom `act()` nhận lỗi thay cho `onUncaughtError`, nên dòng cố định chỉ kiểm được cho lỗi được bắt. App có boundary ở gốc nên lỗi render thường được bắt trước.
- Lỗi ngoài React (promise bị từ chối, lỗi mạng của trình duyệt) không thuộc phạm vi này.
- E2E với backend thật: NOT RUN (không có `APAS_DEMO_PASSWORD`).
- Bundle tăng ~23 kB gzip.

## 5. Rollback
Revert commit: trả `package.json`/`package-lock.json` về React 18.3.1, bỏ option của root (React 18 không có các option này).
