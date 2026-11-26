# P1 · Quality gate — bằng chứng

> 2026-11-26 · issue FE-UI-P1 · quyết định A-80 (`../DECISIONS.md`) · worktree `D:/Project_Private/AI-Agent-fe`, nhánh `feat/FE-UI-P1-design-system` từ `origin/main` `c4edc3b` · chưa commit.
> Môi trường: Windows 11, Node 22.20.0, npm 10.9.3, React 18.3.1, Vitest 5.1.1 (jsdom), axe-core 4.14.1.

## 1. Baseline trước khi sửa (cùng worktree, sau `npm ci`)
| Lệnh | Kết quả |
|---|---|
| `npm ci` + `npm audit` | 0 vulnerabilities |
| `npm run typecheck` | exit 0 |
| `npx vitest run` | exit 0 · 12 file · **224** test passed |
| `npm run build` | exit 0 (JS 275,98 kB) |
| `npm run ds:verify` | exit 0 · 57 file |

## 2. Gate cuối (2026-11-26T08:08Z, code cuối cùng)
| Lệnh | Kết quả |
|---|---|
| `npm run typecheck` (app, node, e2e) | **exit 0** |
| `npx vitest run` | **exit 0** · 14 file · **261** test passed (224 cũ + 37 mới) |
| `npm run build` | **exit 0** · JS 275,98 kB (không đổi: component mới chưa page nào dùng, bị tree-shake) · CSS 15,20 kB (+ `foundation.css`) |
| `npm run ds:verify` | **exit 0** · 73 file (57 + 16) |
| `npm audit` | 0 vulnerabilities; không thêm dependency |
| `git diff --check` | exit 0 |
| `dist/index.html` | 1 `<script type="module" src="/assets/…">`, không inline script, không thuộc tính `style` |
| Lint | **NOT RUN — repo không có lint** (không thêm ESLint: dependency ngoài phạm vi) |

## 3. Test mới
| File | Số test | Nội dung |
|---|---|---|
| `frontend/src/design-system/foundation.test.tsx` | 27 | Toast (role/aria-live theo tone, chuỗi markup hiện như chữ, nút đóng có nhãn, action bằng bàn phím, viewport) · PageHeader (`h1`) · Tabs 3 biến thể (roving tabindex, ←/→/Home/End bỏ qua tab tắt, click) · Tag (nút bật/tắt bằng Enter/Space, nút bỏ có nhãn) · Card (3 biến thể) · Tooltip (mô tả gắn vào control khi focus, Esc, trong Dialog Esc đầu chỉ đóng tooltip, di chuột lên chính tooltip, khoảng hở) · DataTable (header `scope=col`, sắp xếp nội bộ + `aria-sort`, sắp xếp có kiểm soát không tự đổi thứ tự, rỗng, đang tải `aria-busy`, mở dòng bằng Enter/Space, Space trên checkbox không mở dòng, chọn dòng/chọn tất cả) · axe ở mọi trạng thái hiển thị |
| `frontend/config/foundationCss.test.ts` | 10 | đọc file thật (chặn pass rỗng) · không màu cứng · không độ dài cứng ngoài breakpoint và 1px visually-hidden · không cỡ/độ đậm chữ thô · chỉ dùng custom property có trong `tokens.css`/`bundle.css` · `--bp-*` = `breakpoints.ts` · media query chỉ dùng breakpoint · `main.tsx` nạp CSS đúng thứ tự · helper `atLeast`/`below` |

Ghi chú: lần đầu test CSS dùng import `?raw`; dưới Vitest (`css: false`) nó là chuỗi rỗng nên 5 test xanh rỗng. Đã chuyển sang đọc file từ đĩa + test chặn đọc rỗng; sau đó test bắt được 2 lỗi thật (regex font và `--type-button` nằm ở `bundle.css`).

## 4. Mutation (sửa tạm, chạy test, khôi phục; khôi phục kiểm bằng sha256 / `ds:verify`)
| Mutation | Kết quả |
|---|---|
| `foundation.css`: `gap: 8px` · `color: #202020` · `--bp-wide: 1400px` · `var(--space-44)` · `font: 500 14px/1.4 sans-serif` · `@media (max-width: 1000px)` | 6/6 đỏ |
| `Tooltip.jsx` bản gốc design system | 4/4 test Tooltip đỏ |
| `Tooltip.jsx` bỏ capture + `stopPropagation` | test "trong Dialog" đỏ |
| `DataTable.jsx` bỏ dòng chặn `e.target !== e.currentTarget` | 3 test đỏ |
| File lạ `components/data/Stray.jsx` không có trong `sources.json` | `ds:verify` exit 1 |

## 5. Không chạy được / NOT RUN
| Hạng mục | Lý do |
|---|---|
| E2E Playwright với backend thật (hồi quy EP-10a..d) | FE-UI-D13: `APAS_DEMO_PASSWORD` không có trong môi trường. P1 không đổi DOM/hành vi của màn hình hiện có (không page nào dùng component mới; `foundation.css` chỉ thêm class mới + khối `forced-colors`); hồi quy được phủ bằng 224 unit/component test cũ xanh |
| Test responsive theo viewport | jsdom không có layout; chưa có page dùng class layout. Làm ở P2 (Playwright viewport 390/1024/1280/1440) |
| Độ tương phản, forced-colors, trình đọc màn hình (NVDA) | cần trình duyệt thật/công cụ; axe trong jsdom tắt `color-contrast`. Chuyển P2/P6 |
| Test CSP trên bản build (`headers.spec.ts`) | thuộc E2E; cấu hình CSP không đổi (`config/securityHeaders.ts` không sửa), bundle không inline script/style |

## 6. Review advisory
| Reviewer | Kết quả | Xử lý |
|---|---|---|
| security-auditor | 0H / 0M / 4L | L1 Esc trong Tooltip đóng luôn Dialog → **sửa** (capture + stopPropagation, test). L2 Toast hiển thị nguyên chuỗi → quy tắc P2 `useToast` (chỉ mã thông điệp cố định). L3 DataTable sắp xếp/chọn chỉ trên dòng đã tải, so sánh `>`/`<` → quy tắc dùng. L4 `ds:verify` bỏ sót file chép không khai báo → **sửa** script, mutation đỏ |
| ux-workflow-reviewer | 2H / 7M / 5L | H1 vòng focus design system quá nhạt → **OPEN FE-UI-Q01** (không tự đổi token); `foundation.css` dùng outline `--border-focus` + khối `forced-colors`. H2 phím trên control trong dòng mở dòng → **sửa** DataTable (D14), test. M3/M4 test hover chưa thật sự lên tooltip → **thay test** (`user.hover(tip)`). M3 thời điểm đọc mô tả → NOT RUN (NVDA). M5–M9, L → quy tắc dùng trong `../DECISIONS.md` cho P2 |

Reviewer là advisory, không phải bằng chứng tuân thủ.
