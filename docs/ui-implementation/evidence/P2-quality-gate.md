# P2 · Quality gate — bằng chứng

> 2026-11-26 · issue FE-UI-P2 · quyết định A-83 (`../DECISIONS.md` D15–D20) · worktree `D:/Project_Private/AI-Agent-fe`, nhánh `feat/FE-UI-P2-app-shell` từ `main` `9ced1ca`, rebase lên `d846ab6` (PR #66, không đổi frontend; gate chạy lại sau rebase: typecheck 0, vitest 294 passed, build + verify-no-mocks 0, ds:verify 73) · chủ dự án duyệt và commit 2026-11-26.
> Môi trường: Windows 11, Node 22.20.0, npm 10.9.3, React 18.3.1, react-router-dom 7.18.5, Vitest 5.1.1, Playwright 1.65.1 (Chromium 1255).

## 1. Phạm vi đã làm
- `react-router-dom` 7.18.5 ghim (D20); `HashRouter`; bảng route theo vai trò `src/app/routes.ts` (`#/hop-cho`, `#/cong-tac-ai`, `#/su-kien-bao-mat`, `#/chat`); không ID trong địa chỉ, địa chỉ có query → không tìm thấy.
- Khung nhân viên SH-01 theo artboard F2–F5 (`src/app/shells.tsx`, `foundation.css`): nhóm sidebar, breadcrumb nhóm › màn hình, trang tối đa 1192, padding 28/32, gap 20; thu gọn sidebar dưới 1280. Khung khách giữ bố cục EP-10b.
- Màn hình của vai trò khác → "không có quyền" (chữ F6), không gọi API màn hình đó; địa chỉ lạ → "không tìm thấy" (chữ PROPOSED D15); lỗi hiển thị → boundary theo từng màn hình (D15).
- `PageState` (F6), toast chỉ chữ cố định (D16), hết phiên/đăng xuất, skip link, focus `h1` (D17), `document.title`.
- Mock dev/test `src/mocks/` (Vite mode `mock`, D19), `verify-no-mocks` trong `npm run build`, plugin chặn build ở mode `mock` hoặc `NODE_ENV` khác `production`.
- Không sửa page nào của F1–F5 (P3), không backend, không API mới.

## 2. Gate cuối (2026-11-26T09:14Z, code cuối)
| Lệnh | Kết quả |
|---|---|
| `npm run typecheck` | **exit 0** |
| `npx vitest run` | **exit 0** · 17 file · **294** test passed (261 trước P2 + 33 mới); 3 lần chạy liên tiếp trước đó đều 293–294 passed |
| `npm run build` (typecheck + vite build + verify-no-mocks) | **exit 0** · JS 320,50 kB (gzip 96,28; trước P2 275,98 / 81,30 — do react-router) · CSS 15,70 kB · "production bundle has no mock code" |
| `npm run ds:verify` | exit 0 · 73 file |
| `npm audit` | 0 vulnerabilities |
| `npm run e2e:shell` (Playwright, không backend) | **exit 0** · **9 passed**: 1440/1280 (sidebar 248 px mở, `.ap-page` 28px/32px/20px/1192px, không cuộn ngang), 1279/1024 (sidebar 64 px thu gọn, mở được), khách 390 (không sidebar, không cuộn ngang), deep link vai trò khác, skip link + focus `h1` sau đăng nhập, forced colors có outline, **bản build dưới CSP bắt buộc của `vite preview`: 0 vi phạm CSP** |
| `git diff --check` | exit 0 |
| Lint | NOT RUN — repo không cấu hình lint |

## 3. Test mới
| File | Nội dung |
|---|---|
| `src/app/shell.test.tsx` (18) | route: mỗi vai trò đúng một màn hình, vai trò khác → forbidden, địa chỉ có ID/query → not found; khung 3 vai trò nhân viên (nhóm, breadcrumb, `aria-current` duy nhất, title, `main#noi-dung-chinh`, axe); 4 cặp vai trò × địa chỉ của vai trò khác: chữ F6, không mã tham chiếu, **không lời gọi API của màn hình đó**, nút về trang chính; not found; khách không sidebar; focus `h1` + skip link; focus sau đổi route; đăng xuất về `#/`; hết phiên giữ địa chỉ; sidebar theo bề rộng (matchMedia giả) |
| `src/app/states.test.tsx` (11) | PageState: loading (status + nhãn), forbidden (chữ F6, chỉ `REQ-`), timeout, error (không lộ 500/mã/thông điệp), empty + not-found dùng chữ của màn hình; toast: chỉ chữ cố định, lỗi không bao giờ hiện `error.message`, gộp trùng + tối đa 3, không tự đóng, không nhận chuỗi tự do (kiểm bằng `tsc`); boundary: không lộ thông điệp lỗi, có "Tải lại trang"; axe |
| `src/mocks/backend.test.ts` (4) | mọi câu trả lời mock qua decoder thật của app (fail closed); đăng nhập mock; 403 theo vùng URL; 404 |
| `config/foundationCss.test.ts` | thêm: hằng trang `--page-*`, `100vh` |
| `e2e-shell/shell.spec.ts`, `e2e-shell/bundle.spec.ts` | xem §2 |

Sửa test cũ: `src/test/setup.ts` đặt lại địa chỉ sau mỗi test (hash của test trước làm đỏ 4 test App).

## 4. Mutation (sửa tạm, chạy, khôi phục)
| Mutation | Kết quả |
|---|---|
| `resolve()` bỏ kiểm vai trò | 6 test đỏ |
| toast hiện `error.message` | 1 test đỏ |
| bỏ tự thu gọn dưới 1280 | 1 test đỏ |
| `main.tsx` bỏ điều kiện `DEV` (mock vào bundle) | `verify-no-mocks` exit 1 (tìm thấy marker, `installMocks`, `mock-csrf-token`) |
| `vite build --mode mock` / `NODE_ENV=development vite build` | build bị từ chối |

## 5. Không chạy được / NOT RUN
| Hạng mục | Lý do |
|---|---|
| E2E với backend thật (`npm run e2e`, hồi quy EP-10a..d) | `APAS_DEMO_PASSWORD` không có trong môi trường (D13). Thay thế một phần: 294 unit test (gồm toàn bộ test cũ của F1–F5) + `e2e:shell` (khung thật trong trình duyệt, bản build dưới CSP). Rủi ro: selector của E2E cũ phụ thuộc DOM khung (nav "Điều hướng chính", nút "Đăng xuất", `h1`) — đã giữ nguyên |
| Trình đọc màn hình (NVDA), đo tương phản | cần công cụ ngoài |
| Reflow 320 px / zoom 400 % cho nhân viên | chưa có thiết kế dưới 1024 (D08 PROPOSED) |

## 6. Test chập chờn có sẵn (không do P2)
`src/pages/inspector/SecurityInspectorPage.test.tsx` › "checks a custom window at the field and sends nothing while it is invalid" chạy riêng 3,2 s, sát ngưỡng 5 s; một lần chạy toàn bộ bị quá thời gian. Trang và test không đổi trong P2. Đề xuất issue riêng: tăng timeout của test đó hoặc tách bước gõ.

## 7. Review advisory
| Reviewer | Kết quả | Xử lý |
|---|---|---|
| security-auditor | 0H / 3M / 5L | M1 build mock lọt → **sửa** plugin chặn build mode `mock`/`NODE_ENV`; kiểm lại ở `pom.xml` khi đóng gói: chưa làm (backend, issue riêng). M2 `verify-no-mocks` yếu → **sửa**: bỏ chặn `DEV` bên trong `install.ts` (che marker), marker luôn được tham chiếu (header `X-Apas-Mock`), quét mọi loại file; mutation đỏ. M3 React tự ghi lỗi render ra console → **sửa chú thích**, ghi rủi ro chấp nhận (thông điệp `ApiError` cố định; tắt hẳn cần React 19, quyết định chủ dự án). L1 toast còn sau đăng xuất → **sửa** (ToastProvider trong phần đã đăng nhập). L2 test CSP chưa chạy khung đã đăng nhập → ghi nhận (cần stub `/api` dưới preview; P3). L3 query trong hash → **sửa** (not found + test). L4 ghi nhận. L5 kiểm: `cookie`, `set-cookie-parser` không có trong bundle |
| ux-workflow-reviewer | 0H / 7M / 6L | M1 `main` không tên → **sửa** `aria-label="Nội dung chính"`. M2 focus mất sau đăng xuất/hết phiên/Thử lại (ngoài khung) → P3 (E7 đăng nhập). M3 câu "Không có thay đổi nào được thực hiện" không kiểm chứng được → **sửa chữ**. M4 toast đẩy mất lỗi chưa đọc → **sửa** gộp trùng + test. M5 test kiểu `@ts-expect-error`: có hiệu lực vì `tsc` kiểm `src` gồm file test. M6 reflow 320 px → NOT RUN (chưa thiết kế). M7 toast cảnh báo là `role=alert` → do component design system (giữ nguyên bản sao). L1 sidebar "Công tắc AI" khác `h1` "Công tắc trợ lý AI" → đúng như artboard F5. L2 chữ tắt từ nhãn vai trò → lệch đã biết (D18). L3 forbidden khác not found → chữ F6 đã duyệt. L6 chưa test việc vượt 1280 đặt lại lựa chọn → ghi nhận. Test yếu → **bổ sung** not-found, axe cho error, bề rộng thu gọn 64 px trong Playwright |

Reviewer là advisory, không phải bằng chứng tuân thủ.
