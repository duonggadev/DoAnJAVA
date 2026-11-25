# Sổ Lớp — Tổng quan dự án (bàn giao)

> Cập nhật ngày 25/11/2026. Đây là **bản web** (React/Vite + Spring Boot), **không phải app desktop**.
> Trạng thái: hoàn tất nghiệp vụ, đã chuyển toàn bộ sang MySQL 8, đã kiểm thử mức L4 (full suite + browser render).
> **Tất cả thay đổi hiện chưa commit.**

---

## 1. Tóm tắt điều đã làm

| Hạng mục | Trước | Sau |
| --- | --- | --- |
| CSDL mặc định | SQLite (`backend/data/so-lop.db`) | **MySQL 8** (`solop_enterprise`) |
| Driver / schema | SQLite + `schema.sql` | `com.mysql.cj.jdbc.Driver` + `schema-mysql.sql` |
| Test | SQLite | MySQL `solop_enterprise_test` (38 ca) |
| Script trình duyệt | CSDL tạm SQLite | CSDL tạm MySQL `solop_verify_*` (tự tạo/xóa) |
| Dữ liệu cũ | — | Đã migrate sang MySQL, giữ nguyên id + mật khẩu BCrypt |
| Khởi động | Hai lệnh tay | **File `chay-solop-web.bat`** (tự build, tự dọn process) |
| Giao diện `/unslop` | — | Giữ nguyên tính năng/nội dung/palette/type/layout, chỉ scope màn hình admin |

---

## 2. Kiến trúc và công nghệ

- **Frontend**: React 19 + Vite, cổng **5173**, giao diện tiếng Việt, font DM Sans, icon Lucide, chuyển động GSAP. Chuyển tiếp `/api` sang backend.
- **Backend**: Java 21 + Spring Boot 3.5 + Spring Security (session, BCrypt, CSRF), Spring JDBC, Apache POI, cổng **8098**.
- **CSDL**: MySQL 8.0.30 (Laragon), 20 bảng InnoDB, khóa ngoại thật. `schema-mysql.sql` tạo bảng lõi; `InstitutionSchema`, `OperationsStore`, `ScheduleRules` bổ sung lúc startup.
- **Bảo mật**: cookie thiết bị có chữ ký + HttpOnly, QR HMAC xoay mỗi 20s, PIN 8 số, hash thiết bị theo buổi, thu hồi phiên sau đổi thông tin xác thực.

### Cổng và môi trường

| Thành phần | Giá trị |
| --- | --- |
| Frontend | `http://127.0.0.1:5173` |
| Backend API | `http://127.0.0.1:8098` |
| MySQL | `127.0.0.1:3306` |
| CSDL production | `solop_enterprise` |
| CSDL test | `solop_enterprise_test` |
| CSDL tạm (browser verify) | `solop_verify_workflows`, `solop_verify_onboarding`, `solop_verify_qr` |

---

## 3. Cách chạy (khuyến nghị)

**Double-click `chay-solop-web.bat`** ở thư mục gốc. Script `scripts/run-web.ps1` phía sau sẽ:

1. Kiểm tra cổng 8098/5173 — nếu bị **ứng dụng khác** chiếm thì **báo lỗi và dừng**, không giết process lạ.
2. Cảnh báo nếu MySQL chưa nghe cổng 3306 (không tự khởi động MySQL — cần Laragon chạy sẵn).
3. Tự build backend nếu chưa có jar hoặc mã nguồn mới hơn jar.
4. Khởi động backend (8098) + frontend (5173), mở trình duyệt `http://127.0.0.1:5173`.
5. Nhấn **Enter** hoặc đóng cửa sổ → `taskkill /T /F` kill cả cây tiến trình (java/cmd/npm/node) → kiểm tra lại và báo "Đã giải phóng cổng 8098 / 5173".
6. Đăng ký `PowerShell.Exiting` để dọn cả khi đóng cửa sổ đột ngột.

Chạy thủ công (nếu cần):

```powershell
cd backend; mvn spring-boot:run     # cửa sổ 1
cd frontend; npm.cmd ci; npm.cmd run dev   # cửa sổ 2
```

Yêu cầu: JDK 21, Maven 3.9, Node.js ≥ 22.12, MySQL 8 đang chạy.

---

## 4. Cơ sở dữ liệu MySQL

### Khởi tạo một lần

```sql
CREATE DATABASE solop_enterprise CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'solop_app'@'localhost' IDENTIFIED BY 'Solop2026!';
CREATE USER 'solop_app'@'127.0.0.1' IDENTIFIED BY 'Solop2026!';
GRANT ALL PRIVILEGES ON solop_enterprise.* TO 'solop_app'@'localhost';
GRANT ALL PRIVILEGES ON solop_enterprise.* TO 'solop_app'@'127.0.0.1';
```

### Cấu hình

- Mặc định: `application.properties` → `jdbc:mysql://127.0.0.1:3306/solop_enterprise`, user `solop_app`.
- Đổi qua biến môi trường: `SOLOP_DB_URL`, `SOLOP_DB_USER`, `SOLOP_DB_PASSWORD`.
- CLI MySQL (Laragon, không có trong PATH): `D:\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysql.exe -u root --protocol=TCP -h 127.0.0.1 -P 3306`.

### Migration & rollback

- Chuyển dữ liệu: `python scripts/migrate-sqlite-to-mysql.py --truncate` — chép 20 bảng, giữ nguyên **id và mật khẩu BCrypt**.
- Đối chiếu: `python scripts/verify-migration.py` — khớp số bản ghi, id/mật khẩu, không bản ghi mồ côi, khóa ngoại enforced.
- Kết quả migrate: 8 users, 2 môn, 2 lớp, 7 buổi học, 12 đăng ký, 13 điểm danh, 76 log, 1 phiên bản.
- **Rollback**: giữ `backend/data/so-lop.db`, khởi động `--spring.profiles.active=sqlite` (profile `sqlite` đọc `jdbc:sqlite:${SOLOP_DB:./data/so-lop.db}`).
- Trước khi nâng cấp CSDL thật: sao lưu bằng `mysqldump` (minh chứng nằm trong bảng `minh_chung`).

---

## 5. Tài khoản mẫu

| Vai trò | Tài khoản | Mật khẩu |
| --- | --- | --- |
| Ban đào tạo | `admin` | `DemoSoLop2026` |
| Giảng viên | `gv01` | `DemoSoLop2026` |
| Sinh viên | `sv01` | `DemoSoLop2026` |

Dữ liệu mẫu chỉ tạo khi bảng `nguoi_dung` còn trống. Đặt `SOLOP_DEMO=false` trước lần đầu nếu cần CSDL trống. (`sv50` / `matkhau123` chỉ tồn tại trong CSDL tạm của `verify-admin-onboarding.py`.) Hash bcrypt của `admin` đã kiểm tra khớp `DemoSoLop2026`.

---

## 6. Chức năng theo vai trò

- **Ban đào tạo**: dashboard học kỳ; CRUD tài khoản/môn/lớp; khoá/mở khoá, cấp lại mật khẩu; tạo sinh viên kèm hồ sơ hoặc nhập CSV (báo lỗi từng dòng, hoàn tác toàn tệp); thêm/gỡ sinh viên trong lớp; cấu hình học kỳ/thời gian đăng ký/ngưỡng chuyên cần; xuất Excel; mở lại bảng điểm kèm lý do.
- **Giảng viên**: lịch buổi lặp 1–20 tuần, chống trùng lịch; mở/đóng/mở lại điểm danh; QR động + PIN + trình chiếu; điều chỉnh chuyên cần; duyệt đơn/xem minh chứng; nhập điểm; chốt điểm.
- **Sinh viên**: đăng ký/hủy có điều kiện; lịch tuần; xin nghỉ kèm minh chứng (PDF/PNG/JPEG ≤ 2 MB); điểm danh QR/PIN; xem chuyên cần, điều kiện dự thi, kết quả, GPA.
- **Mọi tài khoản**: đăng nhập/đăng xuất/đổi mật khẩu; thu hồi phiên cũ.

Quy tắc nghiệp vụ chi tiết (QR xoay 20s, chốt/mở lại, tính GPA, 0/F, minh chứng, CSV 500 dòng, v.v.) xem `NANG-CAP-UNG-DUNG.md`.

---

## 7. Kiểm thử — kết quả L4 (25/11/2026, đều trên MySQL 8)

| Kiểm tra | Kết quả |
| --- | --- |
| Maven JUnit (`solop_enterprise_test`) | **38 ca qua, 0 lỗi** (18+4+6+5+5) |
| Node test bộ phân tích QR | 3 ca qua |
| Vite build | Qua |
| `verify-workflows.py` | Qua (CRUD, lịch định kỳ, xin nghỉ/duyệt, đăng ký, điểm, Excel, chốt/mở lại, phân quyền, đổi mật khẩu, modal mobile) |
| `verify-admin-onboarding.py` | Qua (tạo hồ sơ, nhập CSV lỗi từng dòng, giao diện 375/768/1440) |
| `verify-qr.py` | Qua (giải mã QR thật, mở link, đăng nhập chậm 30s, đúng buổi/bản ghi) |
| `verify-migration.py` | Qua (20 bảng khớp số bản ghi, id/mật khẩu, không mồ côi, FK enforced) |
| Smoke API production | Qua (admin login 204, `/api/classes` → `IT204.01`, `IT306.02`) |

Screenshot: `verification/admin-students-{1440,768,375}.png` (tạo lại 25/11/2026).

Lệnh chạy lại:

```powershell
mvn -f backend/pom.xml test
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
python scripts/verify-workflows.py
python scripts/verify-admin-onboarding.py
python scripts/verify-qr.py
python scripts/verify-migration.py
```

Các script Python cần `playwright`, `pymysql`, Chrome; chạy tuần tự (workflows + qr dùng chung cổng 8107/5184; onboarding dùng 8109/5185). Cần JDK 21. `verify-*.py` tự tạo/xóa CSDL tạm, không đụng CSDL thật; cần tài khoản admin MySQL (mặc định root không mật khẩu, đổi qua `SOLOP_DB_ADMIN_USER` / `SOLOP_DB_ADMIN_PASSWORD`).

> Lưu ý PowerShell: dùng `$LASTEXITCODE` khi verify Maven; không dùng `2>&1 | Select-Object -Last` (che lỗi). `npm.cmd --prefix frontend test` **không** thêm `-- --run`.

---

## 8. File và thư mục chính

| Đường dẫn | Vai trò |
| --- | --- |
| `chay-solop-web.bat` | File bat khởi động (double-click) |
| `scripts/run-web.ps1` | Điều phối: build, start, dọn process, kiểm tra cổng |
| `backend/src/main/resources/application.properties` | Cấu hình MySQL mặc định |
| `backend/src/main/resources/application-sqlite.properties` | Profile rollback SQLite |
| `backend/src/main/resources/application-test.properties` | Datasource MySQL cho test |
| `backend/src/main/resources/schema-mysql.sql` | Schema MySQL baseline |
| `backend/src/test/java/vn/edu/solop/` | 5 class JUnit (đã chỉnh cho MySQL) |
| `scripts/migrate-sqlite-to-mysql.py` | Chuyển dữ liệu, giữ id + mật khẩu |
| `scripts/verify-migration.py` | Đối chiếu SQLite ↔ MySQL |
| `scripts/mysql_verify_db.py` | Helper tạo/xóa CSDL tạm |
| `scripts/verify-{workflows,admin-onboarding,qr}.py` | Browser verification trên MySQL tạm |
| `backend/data/so-lop.db` | Backup SQLite để rollback |
| `verification/admin-students-*.png` | Screenshot giao diện quản lý sinh viên |
| `README.md` | Hướng dẫn chạy và migration |
| `NANG-CAP-UNG-DUNG.md` | Nghiệp vụ, quy tắc, kết quả kiểm thử |
| `BAO-CAO-TRIEN-KHAI.md` | Báo cáo triển khai, kiến trúc, phân hệ |
| `KE-HOACH-NANG-CAP.md` | Checklist nâng cấp (đã tick) |

---

## 9. Giới hạn đã xác định

- Chỉ kiểm thử tích hợp với MySQL 8.0.30 (Laragon) trên Windows. PostgreSQL/Docker chưa kiểm chứng.
- Camera vật lý và tải đồng thời nhiều người dùng chưa đo; script dùng video mô phỏng trong Chrome.
- Cookie không định danh chắc thiết bị vật lý; đổi trình duyệt/xóa cookie có thể vượt giới hạn. QR còn hiệu lực có thể bị chia sẻ.
- Cập nhật điểm danh dùng polling 4 giây; rate limit + phiên nằm trong một tiến trình — triển khai nhiều instance cần kho dùng chung.
- GPA, cấm thi 0/F, xử lý vắng có phép là quy ước bản này, cần khớp quy chế trường trước khi dùng kết quả chính thức.
- CSDL tải danh mục về để lọc/phân trang trên giao diện; chưa đo hiệu năng với dữ liệu toàn trường.

---

## 10. Trạng thái kết thúc

- ✅ Nghiệp vụ hoàn tất; `/unslop` dọn xong (giữ nguyên giao diện).
- ✅ MySQL là cấu hình mặc định cho app, test và migration; SQLite chỉ còn làm rollback.
- ✅ Dữ liệu cũ đã migrate và đối chiếu; smoke test API production qua.
- ✅ File bat khởi động + tự dọn process/ports đã test thực tế (ports 8098/5173 giải phóng, không còn process java/node tồn dư).
- ✅ Full suite L4 qua.
- ⚠️ **Chưa commit** — chỉ commit khi bạn yêu cầu.
