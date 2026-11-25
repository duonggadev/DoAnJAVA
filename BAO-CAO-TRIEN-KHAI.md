# Báo cáo triển khai Sổ Lớp

Cập nhật 25/11/2026. Đồ án quản lý đào tạo và điểm danh thông minh cho Ban đào tạo, Giảng viên và Sinh viên. Chi tiết quy tắc, kịch bản trình diễn và kết quả kiểm thử tại [Nghiệp vụ và kiểm thử](NANG-CAP-UNG-DUNG.md).

## Kiến trúc

React/Vite nhận truy cập tại cổng 5173, chuyển tiếp /api sang Spring Boot cổng 8098. Backend Java 21 dùng Spring JDBC, Spring Security (session, BCrypt, CSRF), Apache POI và MySQL 8 (driver MySQL, `schema-mysql.sql`, khóa ngoại InnoDB). Cấu hình mặc định dùng CSDL `solop_enterprise`; test dùng `solop_enterprise_test`; script trình duyệt dùng CSDL tạm `solop_verify_*`.

Giao diện tiếng Việt dùng Lucide, CSS và GSAP; nền trung tính, điểm nhấn đỏ, bảng tìm kiếm/sắp xếp/phân trang, hộp thoại và toast. Chuyển động có dọn dẹp theo vòng đời và chế độ giảm chuyển động. Modal và điều hướng mobile hỗ trợ bàn phím, quản lý focus và khóa cuộn.

## Phân hệ

| Vai trò | Chức năng |
| --- | --- |
| Ban đào tạo | Dashboard học kỳ; tài khoản/khóa/đặt lại mật khẩu; môn/tiên quyết/trọng số; lớp/sĩ số/giảng viên; thời gian đăng ký và ngưỡng chuyên cần; báo cáo; mở lại bảng điểm |
| Giảng viên | Lịch từng buổi/lặp tuần; chống trùng lịch; QR động/PIN; điều chỉnh chuyên cần; duyệt đơn và tải minh chứng; nhập/xuất Excel; chốt điểm |
| Sinh viên | Đăng ký/hủy có kiểm tra điều kiện; lịch tuần; xin nghỉ kèm minh chứng; điểm danh xác thực; xem điểm, chuyên cần, GPA |
| Tài khoản cá nhân | Đăng nhập, đăng xuất, đổi mật khẩu; thu hồi phiên cũ |

## Dữ liệu

| Bảng | Nội dung |
| --- | --- |
| nguoi_dung | Mã, tên, email, mật khẩu BCrypt, vai trò, khóa và phiên bản xác thực |
| mon_hoc | Môn, tín chỉ, tiên quyết và ba trọng số |
| lop_hoc | Môn, giảng viên, học kỳ, sĩ số và trạng thái chốt |
| dang_ky | Sinh viên/lớp, ngày đăng ký, điểm thành phần, tổng kết, điểm chữ, cấm thi |
| buoi_hoc | Lịch, phòng, nội dung, PIN, QR secret và hạn điểm danh |
| diem_danh | Trạng thái, thời điểm, ghi chú và hash thiết bị theo buổi |
| hoc_ky | Thời gian đăng ký và ngưỡng chuyên cần |
| don_xin_phep | Lý do, minh chứng, trạng thái và người duyệt |
| minh_chung | Tệp PDF/PNG/JPEG gắn một đơn, truy cập có phân quyền |
| nhat_ky_diem | Lịch sử chốt/mở lại, người thực hiện, thời điểm và lý do |

MySQL có 20 bảng: các bảng lõi từ `schema-mysql.sql` cộng danh mục đào tạo, hồ sơ sinh viên, thông báo/nhật ký và khóa lịch trình do mã Java tạo. Dữ liệu từ bản SQLite cũ được chuyển bằng `scripts/migrate-sqlite-to-mysql.py` (giữ mã số và mật khẩu), đối chiếu bằng `scripts/verify-migration.py`. Tệp `backend/data/so-lop.db` được giữ làm bản rollback qua profile `sqlite`.

## Điểm vào mã nguồn

- SecurityConfig: đăng nhập, CSRF, phân quyền, thu hồi phiên và hạn chế thử đăng nhập.
- AdminController: danh mục, học kỳ, phân công và các ràng buộc quản trị.
- AttendanceController / RotatingQr / QrTickets / BrowserDevice: buổi học, QR/PIN, bằng chứng quét và giới hạn trình duyệt.
- LearningController / ScheduleRules: đăng ký, lịch, điểm thành phần và điều chỉnh chuyên cần.
- AcademicController / GradePolicy: kết quả học tập, điều kiện dự thi, xin phép, chốt/mở lại và xuất báo cáo.
- GradeWorkbookController / EvidenceController / AccountController: Excel, minh chứng và đổi mật khẩu.
- frontend/src/main.jsx / Management.jsx / Study.jsx: các màn theo vai trò.
- Feedback.jsx / Motion.jsx: modal, toast và chuyển động.

## Chạy và kiểm thử

Xem [README](README.md) để chạy. Maven chạy 38 ca hồi quy trên MySQL; Node test có 3 ca QR. scripts/verify-workflows.py kiểm tra nghiệp vụ qua trình duyệt; scripts/verify-admin-onboarding.py kiểm tra tạo hồ sơ, nhập CSV và giao diện 375/768/1440; scripts/verify-qr.py giải mã QR thực từ giao diện và kiểm tra luồng xác thực; scripts/verify-migration.py đối chiếu dữ liệu MySQL với bản backup SQLite.

Bản hiện tại đã kiểm thử với MySQL 8.0.30, Chrome và camera mô phỏng. PostgreSQL, camera vật lý, hiệu năng tải lớn và vận hành nhiều instance cần kiểm chứng riêng. Quy tắc học vụ áp dụng cụ thể được ghi trong tài liệu nghiệp vụ để đối chiếu với quy chế trường.
