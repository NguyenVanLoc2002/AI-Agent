# Ghi chú bản xuất design system (17/11/2026, bổ sung 25/11/2026)

Nguồn: artifact "AutoParts Agent Design System" (phiên bản 1794890360-1656, 425 file). Bổ sung mã nguồn ngày 25/11/2026 từ phiên bản 1795580419-737d (A-18(b), A-66).

## Có đủ trong bản này
- Toàn bộ luật: `README.md`, `CHANGELOG.md`, `guidelines/writing.md`.
- Token: `tokens.json`, `tokens.css`, `design-system.json`.
- Component: `components/bundle.js`, `components/bundle.css`, `components/index.d.ts`, và cho từng component: `README.md` (luật dùng), `.d.ts` (props), `preview.html`; bản chép trong `components/<nhóm>/*.d.ts`, `*.prompt.md`.
- Giấy phép: `assets/fonts/OFL.txt` (SIL OFL 1.1, từ `@fontsource/be-vietnam-pro` 5.3.0) và `assets/icons/LICENSE` (ISC, từ `lucide-static@0.460.0`), bổ sung 17/11/2026.
- Font Be Vietnam Pro 400/500/600/700 (vietnamese, latin, latin-ext) — lấy từ gói `@fontsource/be-vietnam-pro` 5.3.0, cùng nguồn Google Fonts (OFL) với bản gốc.
- 82 icon Lucide — lấy từ gói `lucide-static@0.460.0`, đúng phiên bản design system quy định. Nội dung hình giống bản gốc; file gốc có thể kèm thêm phần chú thích.
- `assets/logo/` — logo phương án A (**PROPOSED**, mới thêm, chưa có trong design system gốc).
- Mã nguồn đủ 41 component + `core/a11y.jsx` trong `components/<nhóm>/*.jsx` (bổ sung 25/11/2026, A-66): 37 file chép nguyên văn, sha256 khớp danh sách file của artifact; mọi import tương đối đều trỏ tới file có trong repo; chỉ import `react`. Không sửa nội dung.

## Không có trong bản này
Không chép được tự động từ artifact; luật tương ứng đã nằm trong README và writing.md:
- `guidelines/*.html` (30 thẻ minh họa màu, chữ, khoảng cách, icon, viết…).
- `tokens/*.css` tách lẻ, `styles.css` (đã gộp trong `tokens.css` + `components/bundle.css`).
- `screens/`, `ui_kits/app/`, `planning/App UX Plan.html`, `doc-page.js`, `_ds_manifest.json`, `assets/notes/` — bản dựng thử ban đầu và công cụ hiển thị, đã được canvas thay thế (`docs/design/`).

## Đã gỡ khỏi bản xuất (17/11/2026)
- `_ds_bundle.js`: gần như trùng `components/bundle.js` (chỉ khác 2 dòng trong comment); chỉ `archived/` dùng tới.
- `archived/`: 7 thẻ cũ có đường dẫn hỏng (`../../styles.css`, `../../_ds_bundle.js`) và nạp script từ CDN unpkg.
- Bản gốc vẫn còn trong artifact Claude Design; xuất lại nếu cần.

## Lưu ý khi dùng cho frontend
- `components/bundle.js` là IIFE gắn vào `window.AutoPartsAgentDesignSystem_aa8546`, cần biến toàn cục `React`, và **tự render UI kit demo vào `#root`**, ghi `localStorage` (`ap-route`), gắn `window.PDData/PLData/APData`. Không chèn vào app sản phẩm.
- Dùng mã nguồn `components/<nhóm>/*.jsx` (đủ từ 25/11/2026, A-18(b) đã đáp ứng), không dùng `bundle.js`. Mã nguồn là DESIGN PROPOSAL: khi đưa vào `frontend/` vẫn qua review như mã của repo.
- `core/Icon.jsx` ghép prop `title` vào `dangerouslySetInnerHTML` không escape: chỉ truyền chuỗi cố định, không truyền dữ liệu từ người dùng, model hay ERP; khi đưa vào `frontend/` thì sửa thành phần tử `<title>` của React (A-66, việc của EP-10a).
- `design-system.json` trỏ tới đường dẫn `project/…` không tồn tại và `assetGroups` rỗng: không dùng bản này để đồng bộ ngược lên Claude Design.
- `components/Cover/` là thẻ ảnh bìa, không phải component.

## Lưu ý mâu thuẫn
README ghi "No logo exists… Do not draw a mark." Logo trong `assets/logo/` là đề xuất mới; khi chủ dự án duyệt thì cập nhật dòng này trong README.
