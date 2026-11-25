# Sổ Lớp — Design Spec v2

**Hướng:** Sổ cái học vụ (Academic Ledger)
**Trạng thái:** DRAFT, chờ duyệt trước khi sửa code
**Ngày:** 24/11/2026
**Phạm vi:** `frontend/` (React 19 + Vite). Không đổi backend, API, schema hay dữ liệu demo.
**Track:** A (sản phẩm, cấu trúc, copy) → B (hệ thống visual, motion).

---

## 0. Nguồn tham chiếu và bằng chứng

### 0.1 Đã đọc

| Nguồn | Kết quả |
|---|---|
| `D:\skills\designer\references\landing-page-design.md` | Đọc đủ **474 dòng** (bản local; skill ghi upstream 479) |
| `ai-chan-motion-designer-skill` | Token 120/240/480ms, một easing family, reduced-motion là state thiết kế |
| `frontend/src/style.css` | 1.178 dòng, đọc đủ |
| `main.jsx` 576 · `Management.jsx` 484 · `Institution.jsx` 261 · `Study.jsx` 114 · `Feedback.jsx` 136 · `Motion.jsx` 37 · `CheckInLanding.jsx` 67 · `QrCamera.jsx` 67 | Đọc đủ |
| Ảnh hiện trạng | `C:\Users\Rachel\AppData\Local\Temp\opencode\ui-shots\after\` (38 ảnh, 1440/768/390) |

### 0.2 File thiết kế AI-chan

**Không tìm thấy file export riêng cho Sổ Lớp.** Đã quét:

- `D:\NguyenThaiDuong\Study` (mọi thư mục, trừ `node_modules`, `dist`, `.runtime`)
- `D:\cache\AI-agent\Ai-chan`
- `D:\skills\designer`
- `Downloads`, `Desktop`, `Documents`

Bộ duy nhất có sẵn là 5 ảnh ref của `ai-chan-unslop-ui` (dashboard dark neon, lệch brief) và doctrine `landing-page-design.md`. Vì vậy:

- Spec này là **nguồn chuẩn tạm thời** cho Phase 0–6.
- Nếu bạn bổ sung file export (ảnh/Figma/spec), tôi sẽ diff với spec này **trước khi sửa code**, không tự ý trộn hai hệ.
- Không coi ảnh dark neon là chuẩn; nó ngược yêu cầu "không gradient lòe loẹt, không template AI".

### 0.3 Hiện trạng đáng giữ

App đã có một hệ ấm và mạch lạc: rail mực, giấy kem, accent gạch, DM Sans, lucide. Spec **tiến hóa** hệ này, không dựng hệ thứ hai. Giữ:

- DM Sans 400/500/600 (`style.css:9`)
- lucide 16px stroke 1.6 (`style.css:149`)
- GSAP + `@gsap/react` (đã có trong `package.json`)
- Bố cục rail + toolbar + content, drawer mobile, dialog native `<dialog>`
- Toàn bộ class hook hiện có để diff nhỏ, dễ rollback

---

## 1. Track A — Sản phẩm, cấu trúc, copy

### 1.1 Một việc chính cho mỗi vai trò

| Vai trò | Một việc chính | Màn vào | CTA chính | CTA phụ |
|---|---|---|---|---|
| Ban đào tạo | Biết hôm nay có gì bất thường và xử lý | Tổng quan đào tạo | Tạo lớp học phần | Cần chú ý, Quản lý lớp |
| Giảng viên | Mở điểm danh đúng buổi | Sổ lớp học phần | Mở điểm danh | Trình chiếu QR, Xem kết quả |
| Sinh viên | Xem tuần này học gì, kết quả ra sao | Lịch học và kết quả | Xác nhận điểm danh (khi có QR) | Xin phép nghỉ |
| Khách | Đăng nhập hoặc điểm danh | Đăng nhập | Đăng nhập | Về sổ lớp (check-in) |

Quy tắc: mỗi màn chỉ một CTA filled (ink). Các nút còn lại là secondary hoặc ghost. Không đặt hai CTA cạnh tranh trong cùng một header.

### 1.2 Inventory màn hình và trạng thái

| # | Màn | Component | Dữ liệu | Trạng thái cần có |
|---|---|---|---|---|
| 1 | Đăng nhập | `main.jsx:30` | `/login`, `/me`, QR ticket | rest, loading, error, pending QR |
| 2 | Check-in | `CheckInLanding.jsx:41` | `/check-in-context`, `/check-in` | loading, open, closed, done, error |
| 3 | Shell | `main.jsx:160` | `/classes` | loading, empty, error, drawer, search |
| 4 | Tổng quan đào tạo | `Management.jsx:197` | `/admin`, `/admin/attendance` | loading, metric error, empty alerts |
| 5 | Học kỳ / Tài khoản / Môn học / Lớp | `Management.jsx:9`, `:197` | `/admin/{section}` | table, sort, search, empty, editor, delete |
| 6 | Đăng ký lớp | `Management.jsx:351` | `/enrollment` | table, disabled reasons, confirm |
| 7 | Bảng điểm | `Management.jsx:385` | `/classes/:id/grades` | editable, finalized, history, import |
| 8 | Kết quả điểm danh | `Management.jsx:455` | `/session-management/:id/attendance` | table, leave request, approve |
| 9 | Cơ cấu đào tạo | `Institution.jsx:120` | 6 entity + catalog | tabs, search, pagination, empty, error |
| 10 | Khung môn học | `Institution.jsx:163` | `/programs/:id/courses` | loading, add, remove, confirm |
| 11 | Chương trình học | `Institution.jsx:208` | `/my-program` | no profile, no program, progress |
| 12 | Nhật ký hệ thống | `Institution.jsx:236` | `/admin/audit` | search, pagination, empty |
| 13 | Thông báo | `Institution.jsx:244` | `/notifications` | unread, read, empty, mark all |
| 14 | Sổ lớp | `main.jsx:292` | `/classes/:id` | schedule, report, grades, empty |
| 15 | Tạo/sửa buổi | `main.jsx:380` | `/sessions` | form, busy, error |
| 16 | Chi tiết buổi | `main.jsx:416` | QR, PIN, check-in | live, closed, confirmed, camera |
| 17 | Lịch học và kết quả | `Study.jsx:18` | `/my-study` | loading, empty week, GPA empty, leave |
| 18 | Xuất dữ liệu | `Study.jsx:105` | file links | hover, focus |
| 19 | Đổi mật khẩu | `main.jsx:132` | `/account/password` | busy, mismatch, error |
| 20 | Tìm lớp | `main.jsx:283` | client filter | empty, keyboard |
| 21 | Modal / Confirm / Toast | `Feedback.jsx:54`, `:111` | — | enter, exit, busy, error |
| 22 | Camera QR | `QrCamera.jsx:19` | getUserMedia | starting, error, close |

### 1.3 Copy

Giữ giọng hiện tại: cụ thể, điềm, câu ngắn, không hype. Không dùng "chào mừng", "đột phá", "tối ưu hóa", "trải nghiệm tuyệt vời", dấu chấm than.

| Chỗ | Hiện tại | Đề xuất | Lý do |
|---|---|---|---|
| Login cover | "Điểm danh QR, chuyên cần theo buổi và kết quả học tập của từng sinh viên, cập nhật ngay khi lớp kết thúc." | "Điểm danh QR, chuyên cần và kết quả học tập, cập nhật ngay khi lớp kết thúc." | Bớt 8 chữ, cùng nghĩa, 2 dòng ở 390 |
| Admin overview | "Theo dõi lớp học và những sinh viên cần được hỗ trợ." | Giữ | Đúng một việc |
| Role summary | "Cộng đồng học tập" | "Phân bố tài khoản" | Cụ thể hơn, không ẩn dụ |
| Study empty week | "Tuần này chưa có buổi học" + "Chọn tuần khác…" | Giữ, thêm nút "Về tuần này" | Có hành động ngay |
| Table empty | "Không có kết quả phù hợp" | "Không có kết quả cho “{query}”" + "Xóa bộ lọc" | Nêu nguyên nhân, có lối ra |
| Error panel | Chỉ câu lỗi | Câu lỗi + nút "Thử lại" khi lỗi tải | B9: lỗi phải có bước tiếp |

Quy tắc:

- Sentence case cho heading và button.
- Không hyphen trong câu; dùng en dash cho khoảng thời gian (`08:00–10:00`).
- Không để một từ lẻ trên dòng cuối: `text-wrap: balance` cho heading, `pretty` cho body.
- Mã lớp, mã môn, `2026-1`, `IT204.01` là dữ liệu, được giữ nguyên.
- Không bịa số, logo, testimonial. Số liệu lấy từ API; màn trống là dữ liệu thật.

### 1.4 Thứ tự build

1. Token + primitive (button, input, badge, focus, motion token)
2. Shell: rail, toolbar, masthead, drawer
3. Tổng quan đào tạo
4. Các bảng dữ liệu: admin sections, cơ cấu, khung môn, nhật ký, thông báo
5. Sổ lớp: lịch, chi tiết buổi, QR, điểm danh, bảng điểm
6. Sinh viên + public: lịch học, chương trình, login, check-in
7. Motion + a11y + responsive + build/test

Mỗi bước: build, test, screenshot 1440 trước; sửa xong mới sang bước sau. Không rebuild cả trang trong một lần.

---

## 2. Bảng đối chiếu hiện trạng → chuẩn mới

| Màn / vùng | Hiện trạng (bằng chứng) | Chuẩn mới | Vì sao |
|---|---|---|---|
| Token | Ấm, đúng hướng; nhiều hex rải rác ngoài token (`style.css:440`, `:551`, `:883`, `:998`) | Hai lớp: palette → semantic; component chỉ dùng semantic | Một nguồn màu, đổi theme không phải grep hex |
| Nút chính | Accent gạch `#9c3b2c` (`:90`) | Ink `#1c1b18`, chữ paper; accent chỉ cho link/active/destructive | Gạch đang vừa là thương hiệu vừa là nguy hiểm; tách vai trò |
| Tiêu đề trang | h1 30/36 (`:59`) | 36/40 desktop, 30/36 mobile | Phân cấp rõ hơn, vẫn vừa 390 |
| Header trang | Không có rule; eyebrow accent (`:354`) | Masthead: rule 24×2 accent, eyebrow muted, h1 ink, rule chân 1px | Một điểm nhấn, không tô đỏ mọi nhãn |
| Panel | `.report` bo 12, viền 1px cho gần như mọi thứ (`:391`) | "Sheet" chỉ cho vùng dữ liệu; page dùng rule + khoảng trắng | Bớt card soup, tăng cảm giác biên tập |
| Spacing | 6/10/14/20/28/36/72 (`:73`, `:260`, `:400`, `:548`, `:1090`) | 8pt: 2/4/8/12/16/24/32/40/48/64/80/96 | Nhịp đều, dễ scan |
| Bảng | Cell 14/20 (`:548`), header 12 (`:553`) | Row 48, cell 16/24; header 12/16; số căn phải | Dữ liệu đọc nhanh hơn, giống sổ cái |
| Stats | 3 nút trong card, viền trái giữa các nút (`:439`) | Band lưới 1px, cell paper, metric 36/40 | Một khối liền, không phải 3 card |
| Sidebar | 264px, active là pill sáng (`:248`) | 240px, active = rule trái 2px accent + 4% trắng | Index rail, không phải menu button |
| Toolbar | 72px, blur luôn (`:304`) | 64px, blur ≥900; mobile opaque | Nhẹ máy, tiết kiệm chiều cao |
| Drawer | Không có nút đóng; JS resize 760 (`main.jsx:212`) lệch CSS 900 (`style.css:1036`) | 288px, nút đóng 44, trap focus, resize 900 | Sửa bug thật, đủ target chạm |
| Search bảng | 300px, 170px ở 390 (`:1164`) | 280px; <600 stack full width | Không bóp placeholder |
| Empty | Chữ trơn (`main.jsx:280`, `Management.jsx:118`) | Icon 24 + h3 16 + p 14 + một action | B9: empty là màn có chủ đích |
| Loading | Chữ "Đang tải…" (`main.jsx:272`, `Study.jsx:38`) | Skeleton đúng hình bảng/stats, pulse opacity | Không nhảy layout |
| Toast | 12 radius, chưa có live region (`Feedback.jsx:25`) | `aria-live=polite`, max 3, hover pause | A11y + không rác |
| Modal | Vào 280ms, ra 200ms, y 4 (`Feedback.jsx:74`) | Vào 240 (opacity + scale .98 + y 8), ra 160 | Vật lý, không chậm |
| Page motion | Chỉ opacity (`Motion.jsx:30`) | Opacity .86→1 + y 8→0, 240 | Có chiều, không chóng mặt |
| Login | 48/52, register có shadow (`:813`, `:829`) | 48/56 (tiếng Việt), register phẳng + 1 shadow nhẹ | Dấu tiếng Việt không chạm dòng |
| Study 390 | Stats 3 hàng đã ổn (`:1133`) | Giữ, thêm skeleton và empty tuần có CTA | Không phá cái đang tốt |
| Touch | Row action 30, pagination 32 (`:580`, `:585`) | Hit area 44 ở <900, visual 32 | Ngón tay, không chỉ chuột |
| Motion token | CSS 200/280ms, GSAP power3 (`Motion.jsx:7`) | Một curve `cubic-bezier(.22,1,.36,1)` cho cả CSS và GSAP | Một ngôn ngữ chuyển động |

---

## 3. Track B — Hệ thống visual

### 3.1 Hướng và 5 điểm nhấn

**Sổ cái học vụ**: giao diện đọc như một cuốn sổ đăng ký học vụ được set kỹ. Giấy ấm, mực đậm, gạch son tiết chế, đường kẻ mảnh, số liệu thẳng cột. Không gradient, không kính mờ trang trí, không card bo tròn tràn lan.

5 điểm nhấn:

1. **Masthead rule**: mỗi trang có một đoạn rule 24×2px accent trước eyebrow; chân masthead là hairline 1px. Đây là chữ ký, thay cho việc tô đỏ mọi eyebrow.
2. **Ink primary**: CTA chính là mực đặc, không phải đỏ. Đỏ chỉ là tín hiệu (active, link, cảnh báo, destructive).
3. **Ledger band**: stats là một dải lưới 1px, không phải 3 card rời. Số 36px, nhãn 12px, không icon trang trí.
4. **Ruled data**: bảng 48px row, header 12px, số căn phải, hairline; không zebra, không shadow.
5. **Motion có trọng lượng**: một curve duy nhất, 240ms cho UI, 120ms cho phản hồi, 480ms cho cảnh; không bounce, không parallax.

### 3.2 Màu

Hai lớp. Component chỉ bind semantic, không dùng hex trực tiếp.

**Palette**

| Token | Hex | Dùng |
|---|---|---|
| `paper-0` | `#fffefa` | mặt giấy chính |
| `paper-1` | `#fbf8f0` | header bảng, hover row, nền phụ |
| `canvas` | `#f6f2e9` | nền app |
| `quiet` | `#f1ece1` | search, track, nền nhẹ |
| `line` | `#e6e0d4` | hairline |
| `line-strong` | `#d5cec0` | viền input |
| `ink` | `#1c1b18` | chữ chính, CTA |
| `ink-soft` | `#2b2924` | hover CTA |
| `secondary` | `#565148` | nhãn |
| `muted` | `#6f6a5e` | metadata |
| `accent` | `#9c3b2c` | link, active, focus, destructive, cảnh báo |
| `accent-hover` | `#83301f` | hover destructive |
| `accent-soft` | `#f7e9e3` | selected, nền cảnh báo nhẹ |
| `positive` | `#2e6a51` | thành công |
| `positive-soft` | `#e7f0e9` | badge thành công |
| `warning` | `#8a5a21` | cảnh báo |
| `warning-soft` | `#f8efdc` | badge cảnh báo |
| `rail` | `#211f1a` | sidebar |
| `rail-soft` | `#2a2823` | hover rail |
| `rail-line` | `#3a3731` | rule rail |
| `rail-text` | `#e9e4d8` | chữ rail |
| `rail-muted` | `#a49d8f` | meta rail |

**Semantic**

| Token | Alias | Dùng |
|---|---|---|
| `--bg-app` | canvas | nền |
| `--bg-surface` | paper-0 | sheet, dialog |
| `--bg-subtle` | paper-1 | header bảng, hover |
| `--bg-muted` | quiet | search, track |
| `--text-primary` | ink | h1, body |
| `--text-secondary` | secondary | label |
| `--text-tertiary` | muted | caption |
| `--text-inverse` | rail-text | rail |
| `--border-subtle` | line | rule |
| `--border-strong` | line-strong | input |
| `--action-primary` | ink | CTA chính |
| `--action-primary-hover` | ink-soft | hover |
| `--action-on-primary` | paper-0 | chữ CTA |
| `--action-destructive` | accent | xóa, đóng |
| `--focus-ring` | accent | focus |
| `--status-positive` | positive | thành công |
| `--status-warning` | warning | cảnh báo |

**Tương phản đã đo**

| Cặp | Tỷ lệ | Chuẩn |
|---|---|---|
| ink / paper | 17.07:1 | AAA |
| secondary / paper | 7.80:1 | AAA |
| muted / paper | 5.34:1 | AA |
| rail-text / rail | 12.98:1 | AAA |
| rail-muted / rail | 6.11:1 | AA |
| paper / ink (CTA) | 17.07:1 | AAA |
| positive / positive-soft | 5.47:1 | AA |
| warning / warning-soft | 5.15:1 | AA |
| accent / paper | 6.77:1 | AA |

Quy tắc dùng màu:

- Accent chỉ cho: rule masthead, active nav, tab active, link, focus ring, destructive, chấm live/alert, selected row.
- Không tô accent cho eyebrow, icon trang trí, hay toàn bộ tiêu đề.
- Nền phẳng. Không gradient. Không colored left-border trên card.
- Status không chỉ dựa vào màu: luôn có chữ.

### 3.3 Typography

Một typeface: **DM Sans** 400/500/600 (đã có). Không italic, không uppercase, không weight 700+.

| Style | Size / LH | Weight | Dùng |
|---|---|---|---|
| Display | 48 / 56 | 600 | Cover login |
| Heading 1 | 36 / 40 | 600 | H1 trang ≥600 |
| Heading 1 compact | 30 / 36 | 600 | H1 trang <600 |
| Heading 2 | 20 / 28 | 600 | Section, panel |
| Heading 3 | 16 / 24 | 600 | Card, row title |
| Title | 16 / 24 | 500 | Nav, form section |
| Body | 14 / 20 | 400 | Nội dung |
| Body strong | 14 / 20 | 500 | Khóa bảng, label |
| Caption | 12 / 16 | 400 | Metadata |
| Caption strong | 12 / 16 | 500 | Eyebrow, table head |
| Button | 14 / 20 | 600 | Nút |
| Button small | 12 / 16 | 600 | Row action |
| Metric | 36 / 40 | 600 | Stats |
| Metric small | 24 / 32 | 600 | Stats <600 |

Ngoại lệ có chủ đích: **Display 48/56**. B1 ghi 48/48; tiếng Việt có dấu cần 1.15–1.2 để không chạm dòng. Nếu bạn muốn tuyệt đối theo bảng, phương án thay thế là 36/40.

Quy tắc:

- Tracking: display −0.03em; h1 −0.02em; h2 −0.01em; body 0.
- `font-variant-numeric: tabular-nums` toàn app; số trong bảng, điểm, tín chỉ căn phải.
- `text-wrap: balance` cho h1/h2; `pretty` cho p.
- Không hyphen trong câu; en dash cho khoảng.
- Line-height không tự do: dùng đúng cặp trong bảng, trừ Display đã ghi.

### 3.4 Spacing

Chỉ dùng: 0, 2, 4, 8, 12, 16, 24, 32, 40, 48, 64, 80, 96.
Control height là **size token** riêng: 32, 36, 40, 44, 48, 64 (không phải spacing).

**Bảng quy đổi từ hiện trạng**

| Cũ | Mới | Áp dụng |
|---|---|---|
| 6 | 8 | gap nhỏ |
| 10 | 12 | padding control |
| 14 | 16 | cell bảng |
| 20 | 24 | padding section |
| 28 | 32 | margin |
| 36 | 40 | control |
| 56 | 48 | cột ngày |
| 72 | 64 | toolbar, row |
| 264 | 240 | rail |
| 280 | 288 | drawer |
| 300 | 280 | search bảng |
| 620 | 640 | dialog |
| 1440 | 1280 | content max |
| 168 | 160 | ring |

Giữ nguyên: 400 (form login), 480 (register), 960 (dialog khung môn), 8/12/16/24.

### 3.5 Radius, viền, elevation

| Token | Giá trị | Dùng |
|---|---|---|
| `--r-xs` | 4 | kbd, tag nhỏ |
| `--r-sm` | 6 | control nhỏ |
| `--r-md` | 8 | input, button, sheet nhỏ |
| `--r-lg` | 12 | sheet, band, toast |
| `--r-xl` | 16 | dialog |
| `--r-pill` | 999 | badge, avatar |

- Công thức nested: `inner = outer − gap`, chỉ áp dụng khi gap <32 và kết quả >2. Ví dụ sheet 12, padding 8 → inner 4; padding 24 → để 8, không ép.
- Viền: card/sheet viền **đủ 4 cạnh** hoặc không viền. Rule nội bộ 1px được phép làm phân cách, không phải viền card.
- Shadow: chỉ dialog, toast, register login. Không shadow cho panel, bảng, stats.
- `--shadow-dialog: 0 32px 96px rgba(28,27,24,.28)`; `--shadow-toast: 0 16px 40px rgba(28,27,24,.16)`; `--shadow-sheet: 0 1px 2px rgba(28,27,24,.04)`.

### 3.6 Layout và breakpoint

```
┌────────────┬──────────────────────────────────────────────┐
│ rail 240   │ toolbar 64 (sticky)                          │
│            ├──────────────────────────────────────────────┤
│ wordmark   │ masthead: rule + eyebrow + h1 + desc + CTA   │
│ search     ├──────────────────────────────────────────────┤
│ nav        │ content max 1280, gutter 40                  │
│ courses    │                                              │
│ account    │                                              │
└────────────┴──────────────────────────────────────────────┘
```

- Rail 240; content max 1280; gutter 40 → 32 → 24 → 16.
- Toolbar 64, sticky, border-bottom; blur chỉ ≥900.
- Masthead: padding-top 32, padding-bottom 24, border-bottom 1px, margin-bottom 24.
- Dashboard: band stats 3 cột; dưới là grid 8/4 (lớp + phân bố); "Cần chú ý" full width hoặc 7/5 tùy dữ liệu.
- Sổ lớp: 7/5 (lịch / chi tiết), gap 24; <1024 một cột.
- Form: 2 cột ≥600; 1 cột <600.
- Dialog: 640; khung môn 960; max-height `calc(100dvh - 48px)`.

**Breakpoint**

| Ngưỡng | Thay đổi |
|---|---|
| ≥1200 | rail 240, gutter 40, content 1280 |
| 1024–1199 | gutter 32; dashboard/sổ lớp một cột |
| 900–1023 | rail 240; toolbar 64; bảng dày 16 |
| <900 | drawer 288; toolbar 64; gutter 24; search icon-only |
| <600 | gutter 16; h1 30; form 1 cột; stats stack; table cell 12/16 |
| <460 | cover 30/36; tabs 12; session 44/1fr/70 |

Sửa bug: `main.jsx:212` đang dùng `window.innerWidth > 760`; đổi thành `> 900` cho khớp `style.css:1036`.

### 3.7 Component

| Component | Spec | Ghi chú |
|---|---|---|
| Button primary | ink fill, paper text, 40 cao (44 touch), 8/12 padding, r8, 14/20 600 | hover ink-soft 200ms; active y1; disabled .45 |
| Button secondary | paper, viền line-strong, ink | hover quiet |
| Button ghost / row | transparent, 30 visual, hit 44 <900 | hover quiet |
| Destructive | accent fill, paper | luôn qua Confirm |
| Input / select | 44, r8, viền line-strong, paper | focus accent + ring 3px 16%; label 12/16 600 |
| Search | 36 (44 touch), quiet, icon 14 | focus-within paper + accent; clear X khi có query |
| Tabs | 44, gap 24, underline 2 accent | `aria-pressed`; scroll ngang, không scrollbar |
| Table | header 12/16 500 muted, row 48, cell 16/24, hairline | số căn phải; hover paper-1; `caption` sr-only |
| Pagination | 32 nút, caption 12 | hit 44 <900 |
| Badge | 24, pill, 12/16 500, dot 6 | positive/warning; không accent |
| Sheet | paper, viền 1px, r12, không shadow | section head padding 24 |
| Band | lưới 1px trên nền line, cell paper, padding 24 | metric 36/40 |
| Empty | icon 24 trong ô 40 quiet, h3 16, p 14 max 46ch, 1 action | không minh họa lớn |
| Skeleton | 3 dòng 48, bar quiet, pulse opacity 1→.55→1 1.2s | không gradient |
| Toast | 380 max, r12, viền 1px, shadow-toast, icon 18 | `aria-live=polite`; max 3; hover pause |
| Dialog | 640, r16, header/body/footer 24, backdrop ink 45% + blur 3 | enter 240, exit 160; busy chặn đóng |
| Drawer | 288, rail, nút đóng 44, scrim ink 48% + blur 2 | trap focus, Escape, trả focus |
| Toolbar | 64; location 14/20; search 280; date 12/16; role badge | <1080 ẩn date; <900 search icon; <600 ẩn location |
| Sidebar | 240; wordmark 36; search 40; nav 40 (44 touch); section label 12/16 | active: rule trái 2 accent + 4% trắng |
| QR | 220 max, padding 8 trắng, r8, viền 1px | PIN 30/36 tracking .16em; timer 12 accent |
| Progress | 6 cao, track quiet, fill accent, pill | dùng scaleX, không animate width |
| Calendar | cột ngày 80 (72 <460), date 24/32, session padding 20/24 | hôm nay accent-soft |
| Notification | chấm 6, rule top, padding 24 | unread accent; read muted |

### 3.8 State matrix

| Thành phần | Rest | Hover | Active | Focus | Disabled | Loading | Empty | Error |
|---|---|---|---|---|---|---|---|---|
| Button | ✓ | nền đổi | y1 | ring 2 | .45 | nhãn "Đang…" | — | — |
| Input | ✓ | — | — | ring 3 | quiet | — | — | viền accent + chữ 12 |
| Row | ✓ | paper-1 | — | ring inset | — | skeleton | empty | — |
| Table | ✓ | row | — | ring | — | skeleton 3 dòng | empty + CTA | panel + Thử lại |
| Dialog | ✓ | — | — | native | busy | nút "Đang lưu…" | — | role=alert |
| Drawer | ✓ | nav | — | ring | — | — | — | — |
| Toast | ✓ | pause | — | ring | — | — | — | tone error |
| Tabs | ✓ | line-strong | — | ring | .45 | — | — | — |

### 3.9 Motion

Một easing family. CSS và GSAP dùng **cùng một curve**.

| Token | Giá trị | Dùng |
|---|---|---|
| `--dur-micro` | 120ms | press, hover màu |
| `--dur-ui` | 240ms | page, drawer, modal, toast |
| `--dur-scene` | 480ms | progress, stagger cap |
| `--ease` | `cubic-bezier(0.22, 1, 0.36, 1)` | tất cả |
| `--stagger` | 60ms | danh sách |

GSAP:

```js
CustomEase.create('solop', 'M0,0 C0.22,1 0.36,1 1,1')
export const motionTokens = {micro:.12, ui:.24, scene:.48, ease:'solop', stagger:.06}
```

`CustomEase` đã có trong `node_modules/gsap/CustomEase.js`; không thêm dependency.

**Choreography**

| Khoảnh khắc | From → To | Thời lượng | Ghi chú |
|---|---|---|---|
| Đổi màn | opacity .86→1, y 8→0 | 240 | kill tween cũ; không exit chặn |
| Dashboard lần đầu | panel opacity 0→1, y 8→0 | 240, stagger 60 | tối đa 4 panel, cap 420 |
| Drawer mở | x −100%→0 | 240 | scrim 200; link stagger 40, cap 240 |
| Drawer đóng | x 0→−100% | 200 | không stagger; trả focus |
| Modal mở | opacity 0→1, scale .98→1, y 8→0 | 240 | backdrop 200 |
| Modal đóng | opacity 1→0 | 160 | không scale |
| Toast vào | opacity 0→1, y 8→0 | 240 | max 3 |
| Toast ra | opacity 1→0 | 160 | hover/focus pause |
| Nút nhấn | y 0→1 | 120 | không bounce |
| Progress | scaleX 0→value | 480 | transform-origin left |
| Skeleton | opacity 1→.55→1 | 1200 | chỉ opacity, không gradient |
| Reduced motion | opacity/cut | .01ms | không transform, không stagger |

**Interrupt**

- Đổi route: kill tween page cũ, chạy tween mới; không xếp hàng.
- Drawer: toggle takeover; Escape đóng ngay; resize >900 đóng.
- Modal: đóng sẽ cancel enter; `busy` chặn đóng.
- Toast: tối đa 3; hover/focus dừng timer; error không tự mất.
- Tab nhanh: không stagger, chỉ fade 160.

**Không dùng**

- Không scroll reveal cho app (chỉ landing mới cần).
- Không parallax, không bounce, không xoay, không blur chuyển động.
- Không animate `width`, `height`, `top`, `left`.

### 3.10 Accessibility

| Hạng mục | Yêu cầu | Cách kiểm |
|---|---|---|
| Landmark | `aside`, `nav`, `main`, `header`; skip link | Tab đầu tiên, Enter vào `#main-content` |
| Heading | Mỗi màn một h1; không nhảy cấp | DOM audit |
| Focus | Ring 2px accent, offset 2; không bỏ outline | Tab qua mọi control |
| Drawer | Trap focus, nút đóng, Escape, trả focus, `aria-expanded` | Bàn phím 390 |
| Dialog | `<dialog>`, `aria-labelledby`, focus first field, busy chặn | Bàn phím |
| Touch | 44×44 cho control ở <900; row action có hit area | Đo bounding box |
| Contrast | AA toàn bộ; bảng trên đã đo | Script contrast |
| Form | Label, required, helper `aria-describedby`, lỗi `role=alert` | Form tạo lớp |
| Live | Toast `aria-live=polite`; lỗi `role=alert` | Tạo/xóa thử |
| Motion | `prefers-reduced-motion`: không transform/stagger | Emulate |
| Status | Không chỉ dùng màu; dot + chữ | Badge |
| Bảng | `caption` sr-only, `scope`, `aria-sort`, vùng cuộn có nhãn | Screen reader snapshot |

---

## 4. Phạm vi và không làm

**Làm**

- Toàn bộ 22 surface ở mục 1.2.
- Token, primitive, shell, masthead, bảng, form, dialog, toast, empty, skeleton, motion.
- Sửa bug breakpoint 760→900; thêm nút đóng drawer; hit area 44.
- Viết lại `style.css` theo token mới; sửa JSX tối thiểu cho state/a11y.

**Không làm**

- Không đổi backend, API, schema, dữ liệu demo, quyền.
- Không thêm dependency, font, icon pack, CDN asset.
- Không đổi DM Sans / lucide.
- Không làm dark mode (không được yêu cầu).
- Không bịa logo, testimonial, số liệu, ảnh stock.
- Không restyle file ngoài `frontend/`.
- Không sửa `D:\skills\designer\references\*`.

---

## 5. Kế hoạch thực thi và nghiệm thu

| Phase | Nội dung | Ảnh nghiệm thu | Điều kiện dừng |
|---|---|---|---|
| 0 | Token + primitive + focus + motion token | Login, một bảng | Build, contrast, focus ring |
| 1 | Shell: rail 240, toolbar 64, masthead, drawer 288 | 1440, 900, 390 | Không overflow; drawer trap; resize 900 |
| 2 | Tổng quan: band, ring, cảnh báo, lớp, phân bố | 1440, 768, 390 | Skeleton, empty, số căn phải |
| 3 | Bảng dữ liệu: admin, cơ cấu, khung môn, nhật ký, thông báo | 1440, 390 | Search stack, empty + CTA, dialog |
| 4 | Sổ lớp: lịch, chi tiết, QR, điểm danh, điểm | 1440, 768, 390 | Live/closed/confirmed; bảng điểm |
| 5 | Sinh viên + public: study, program, login, check-in | 1440, 390 | GPA empty, tuần trống, check-in |
| 6 | Motion + a11y + build/test | Toàn bộ | Console sạch, reduced motion, 3 test |

Mỗi phase:

1. Sửa section, không sửa cả file.
2. `npm run build` và `npm test`.
3. Screenshot 1440; sửa xong mới sang 768/390.
4. Ghi `path/file:line` + ảnh vào báo cáo.

**Nghiệm thu cuối (L3)**

- 390/768/1440: không overflow dương; không cắt chữ; CTA chính thấy trong màn đầu.
- Console: chỉ 401 auth probe trước đăng nhập; không 404, không React warning.
- Focus: ring 2px; skip link; drawer trap; dialog focus.
- Reduced motion: không transform; opacity final 1.
- Contrast: các cặp ở 3.2 đạt AA.
- Build + 3 test pass.
- Không hex lạ ngoài token (grep).
- Không gradient, không italic, không uppercase, không border một cạnh trên card.

---

## 6. Rủi ro và câu hỏi mở

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Không có file thiết kế AI-chan | Cao | Spec này là nguồn chuẩn; nếu có file, diff trước Phase 0 |
| Spacing 24 làm bảng loãng | Trung bình | Cell 16/24; <760 dùng 12/16; đo lại 390 |
| Ink primary đổi cảm giác thương hiệu | Trung bình | Một token `--action-primary`; đổi lại accent trong 1 dòng nếu không duyệt |
| Bỏ viền card làm mất nhóm | Trung bình | Dùng rule + khoảng trắng; giữ sheet cho bảng |
| Route fade 240 gây chậm | Thấp | Không exit; kill tween cũ; cap 240 |
| Google Fonts render-block | Thấp | Giữ nguyên; self-host là quyết định riêng |
| Check-in token thật chưa test | Trung bình | Không đổi luồng; test bằng PIN + QR ảnh |
| `scrollbar-gutter` để lại dải 15px | Thấp | Giữ để không nhảy layout; ghi nhận |

**Câu hỏi cần duyệt**

1. Chấp nhận **ink primary** (nút đen) và accent gạch chỉ làm tín hiệu?
2. Chấp nhận **h1 36/40** desktop, 30/36 mobile, rail 240, content 1280?
3. Chấp nhận **Display 48/56** cho tiếng Việt (ngoại lệ B1), hay dùng 36/40?
4. Chấp nhận **7 phase (0–6)** và việc không đổi backend/dữ liệu?

---

## 7. Checklist duyệt

- [ ] Hướng "Sổ cái học vụ" + ink primary / brick signal
- [ ] Type scale + spacing 8pt + rail 240 / content 1280
- [ ] Motion 120/240/480, CustomEase `solop`, reduced motion
- [ ] Phạm vi 7 phase (0–6), không đổi backend/API/dữ liệu
- [ ] Không thêm dependency/font/icon pack
- [ ] Nếu có file AI-chan export, gửi trước Phase 0

Duyệt xong, tôi bắt đầu Phase 0 và dừng sau mỗi phase để bạn xem ảnh.
