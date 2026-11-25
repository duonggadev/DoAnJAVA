# Nâng cấp Laragon và nghiệp vụ đào tạo

Phạm vi: MySQL 8.0/MariaDB cho Laragon + phpMyAdmin; cơ cấu đào tạo, hồ sơ, chương trình, thông báo và nhật ký. Dữ liệu SQLite nguồn được sao lưu và giữ nguyên.

- [x] Chuyển cấu hình mặc định sang MySQL 8, driver `com.mysql.cj.jdbc.Driver` và `schema-mysql.sql`; giữ profile `sqlite` để rollback.
- [x] Chuyển dữ liệu vào CSDL riêng `solop_enterprise` bằng `scripts/migrate-sqlite-to-mysql.py`; đối chiếu bởi `scripts/verify-migration.py`.
- [x] Tích hợp khoa/ngành/khóa/lớp hành chính/hồ sơ, chương trình và tiến độ sinh viên (20 bảng InnoDB, khóa ngoại thật).
- [x] Kiểm thử thông báo, nhật ký, phân quyền và trạng thái học vụ bằng 38 ca JUnit trên `solop_enterprise_test`.
- [x] Kiểm thử trình duyệt trên MySQL tạm (`solop_verify_*`), cập nhật README và tài liệu bàn giao; chạy thử app với CSDL production qua API thật.

API mới: /api/admin/institution/** (ADMIN); /api/my-program (SINH_VIEN); /api/admin/audit (ADMIN); /api/notifications/** (chính người đăng nhập).
Danh mục mới phân trang ở server. Thay đổi cơ cấu dùng khóa ngoại và giao dịch. Nhật ký không lưu mật khẩu, PIN, QR hoặc nội dung minh chứng.

Xác minh ngày 25/11/2026: 38 ca Maven/JUnit qua; Node test 3 ca qua; Vite build qua; Playwright CRUD, onboarding Sinh viên và QR qua trên CSDL MySQL tạm; đối chiếu CSDL nguồn/đích qua (số bản ghi, mã số, mật khẩu, không bản ghi mồ côi); đăng nhập admin và danh sách lớp đọc được từ `solop_enterprise`. CSDL tạm được tạo/xóa tự động. Rollback: giữ `backend/data/so-lop.db` và khởi động với `--spring.profiles.active=sqlite`; trước khi thay đổi CSDL thật sao lưu bằng `mysqldump`. Chưa commit thay đổi.
