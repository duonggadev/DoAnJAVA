# Sổ Lớp

Đồ án Quản lý đào tạo và Điểm danh thông minh. Backend dùng Java 21, Spring Boot, Spring Security và MySQL 8. Giao diện tiếng Việt dùng React và Vite.

## Chạy trên Windows

Cần JDK 21, Maven 3.9 và Node.js 22.12 trở lên, cùng MySQL 8 đang chạy.

Cách nhanh nhất: double-click `chay-solop-web.bat` ở thư mục gốc. File này tự build backend nếu cần, khởi động backend (8098) và frontend (5173), mở trình duyệt. Nhấn Enter hoặc đóng cửa sổ để tự động kill toàn bộ tiến trình con và giải phóng cổng. Nếu cổng 8098/5173 đang bị ứng dụng khác dùng, script báo lỗi và dừng thay vì giết process của project khác.

Cách chạy thủ công: mở hai cửa sổ PowerShell từ thư mục dự án.

Backend

```powershell
cd backend
mvn spring-boot:run
```

Frontend

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Mở [Sổ Lớp](http://127.0.0.1:5173). Spring Boot dùng cổng 8098. Vite chuyển tiếp yêu cầu `/api`. Ứng dụng kết nối MySQL tại `127.0.0.1:3306`, CSDL `solop_enterprise`, tài khoản `solop_app`. Các bảng được tạo tự động từ `schema-mysql.sql` và mã Java khi khởi động lần đầu.

## Cơ sở dữ liệu MySQL

Cần MySQL 8 đang chạy (ví dụ Laragon). Tạo CSDL và tài khoản ứng dụng một lần:

```sql
CREATE DATABASE solop_enterprise CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'solop_app'@'localhost' IDENTIFIED BY 'Solop2026!';
CREATE USER 'solop_app'@'127.0.0.1' IDENTIFIED BY 'Solop2026!';
GRANT ALL PRIVILEGES ON solop_enterprise.* TO 'solop_app'@'localhost';
GRANT ALL PRIVILEGES ON solop_enterprise.* TO 'solop_app'@'127.0.0.1';
```

Đổi thông tin kết nối qua biến môi trường `SOLOP_DB_URL`, `SOLOP_DB_USER`, `SOLOP_DB_PASSWORD` (mặc định khớp lệnh trên). Để khôi phục bản SQLite cũ, chạy `python scripts/migrate-sqlite-to-mysql.py` (xem thêm phần Kiểm thử). Profile `--spring.profiles.active=sqlite` chỉ dùng để rollback và đọc `backend/data/so-lop.db`.

## Dữ liệu và tài khoản mẫu

| Vai trò | Tài khoản | Phân quyền |
| --- | --- | --- |
| Ban đào tạo | `admin` | Quản lý tài khoản, môn và lớp. Xem chuyên cần và điểm của các lớp |
| Giảng viên | `gv01` | Quản lý buổi học, điểm số và chuyên cần của lớp phụ trách |
| Sinh viên | `sv01` | Đăng ký lớp, điểm danh và xem kết quả của mình |

Các tài khoản mẫu dùng mật khẩu `DemoSoLop2026`. Dữ liệu mẫu chỉ được tạo khi bảng `nguoi_dung` còn trống. Đặt `SOLOP_DEMO=false` trước lần khởi động đầu tiên nếu cần CSDL trống. Dữ liệu từ bản SQLite được chuyển sang MySQL bằng `python scripts/migrate-sqlite-to-mysql.py --truncate`; mã số và mật khẩu cũ được giữ nguyên.

## Quản lý đào tạo

- Đăng nhập tài khoản **admin** của Ban đào tạo để mở **Tổng quan đào tạo** với số liệu danh mục, bảng lớp theo học kỳ và phân bố tài khoản theo vai trò.
- Mở **Tài khoản**, **Môn học** hoặc **Lớp học phần** để tạo, sửa hoặc xóa. Biểu mẫu và xác nhận mở trong hộp thoại. Thông báo kết quả hiện ở góc màn hình.
- Tạo giảng viên và môn học trước khi mở lớp học phần. Mã tài khoản, mã môn, mã lớp và email phải duy nhất trong danh mục tương ứng.
- Khi sửa tài khoản, để trống mật khẩu mới để giữ mật khẩu hiện tại. Đổi mật khẩu, vai trò hoặc mã tài khoản sẽ làm phiên cũ mất hiệu lực.
- Một danh mục đã được dữ liệu khác tham chiếu phải được giữ để bảo toàn hồ sơ học tập.
- Tab **Sinh viên** tạo tài khoản kèm hồ sơ: lớp hành chính, ngày sinh, giới tính, điện thoại, trạng thái và chương trình tốt nghiệp. Có thể tạo tài khoản sinh viên không kèm hồ sơ; hồ sơ được thêm khi điền các trường tương ứng.
- Nút **Nhập sinh viên** mở hộp thoại nhập CSV. Tải file mẫu để biết cột; dùng mã lớp hành chính và mã chương trình thay cho mã số. Mỗi lần tối đa 500 dòng. Nếu có dòng lỗi, không sinh viên nào được tạo và bảng lỗi hiển thị theo số dòng.
- Trong **Lớp học phần**, nút **Danh sách sinh viên** cho phép thêm/gỡ sinh viên. Thêm kiểm tra trạng thái tài khoản, sĩ số, trùng môn cùng học kỳ, trùng lịch và lớp đã chốt điểm. Gỡ bị chặn khi đã có điểm, kết quả điểm danh, đơn nghỉ hoặc buổi học đã kết thúc. Sinh viên nhận thông báo khi được xếp hoặc gỡ khỏi lớp.

## Buổi học, điểm danh và điểm số

1. Đăng nhập tài khoản giảng viên, ví dụ **gv01**. Mở lớp Cơ sở dữ liệu và tạo buổi mới. Dùng **Sửa buổi** hoặc **Xóa buổi** khi buổi học chưa kết thúc, chưa từng mở điểm danh và chưa có đơn nghỉ hoặc kết quả.
2. Chọn buổi hôm nay và bấm **Mở điểm danh**. PIN và QR hiệu lực tối đa 10 phút. Giảng viên có thể đóng sớm hoặc mở lại trong ngày. Mở lại sẽ phát mã mới, vô hiệu mã cũ và giữ các kết quả đã ghi nhận.
3. Mở cửa sổ ẩn danh, đăng nhập sinh viên, ví dụ **sv01**, rồi vào cùng lớp và buổi. Nhập PIN, quét bằng camera hoặc đọc ảnh QR. Bấm **Xác nhận điểm danh**.
4. Chọn **Xem kết quả** ở phiên giảng viên để ghi nhận hoặc điều chỉnh trạng thái và ghi chú. Dùng **Chuyên cần** để xem số buổi tham dự, số buổi vắng và tỷ lệ của từng sinh viên.
5. Trong **Bảng điểm**, nhập điểm quá trình và điểm thi từ 0 đến 10. Sinh viên chưa đạt ngưỡng chuyên cần chưa được nhập điểm thi. Khi chốt, trường hợp cấm thi nhận 0/F; các sinh viên còn lại cần đủ điểm thành phần. Ban đào tạo có thể mở lại kèm lý do, giảng viên điều chỉnh rồi chốt lại.
6. Sinh viên mở **Đăng ký lớp** để đăng ký khi còn hạn, đủ sĩ số, đạt tiên quyết và không trùng lịch/cùng môn trong học kỳ. Hủy chỉ được thực hiện trước khi có điểm, điểm danh, đơn nghỉ hoặc buổi đã kết thúc.

Camera cần quyền truy cập từ trình duyệt. `localhost` và `127.0.0.1` có thể dùng camera khi trình duyệt hỗ trợ. Khi thử qua địa chỉ mạng, trình duyệt thường yêu cầu HTTPS.

Báo cáo chuyên cần tính buổi đã đóng điểm danh hoặc buổi chưa mở đã kết thúc giờ học theo múi giờ Việt Nam. Đi muộn tính là tham dự, vắng có phép được tính riêng với vắng. Với đăng ký mới, báo cáo bỏ qua các buổi trước ngày đăng ký. Đăng ký có sẵn từ bản trước giữ cách tính lịch sử.

## Quét bằng Camera hoặc Zalo từ điện thoại

Frontend nhận kết nối trong mạng LAN ở cổng 5173. Khi chạy phát triển, Vite chọn địa chỉ IPv4 của Wi-Fi hoặc Ethernet để tạo liên kết QR. Nếu máy có nhiều mạng, đặt SOLOP_PUBLIC_ORIGIN trong frontend/.env.local thành origin truy cập thực tế, ví dụ http://192.168.2.8:5173, rồi khởi động lại frontend.

Điện thoại và máy tính cần cùng mạng. Mở địa chỉ LAN của frontend trên điện thoại và đăng nhập tài khoản sinh viên. Sau đó dùng Camera hoặc Zalo quét QR mới trên màn hình giảng viên. Liên kết yêu cầu nhập tài khoản và mật khẩu, kể cả khi trình duyệt đang giữ phiên cũ. Sau đăng nhập, trang hiển thị họ tên và MSSV trước khi xác nhận. Máy chủ lấy danh tính từ phiên đã xác thực, không nhận MSSV từ nội dung QR. QR cũ trước bản sửa chỉ có chuỗi token, cần mở lại màn hình giảng viên để nhận QR dạng liên kết.

QR được làm mới mỗi 20 giây. Khi QR hợp lệ lúc mở trang, máy chủ giữ bằng chứng quét trong tối đa hai phút trên chính trình duyệt đó, để hoàn tất đăng nhập dù QR đã xoay. Bằng chứng dùng một lần, mất hiệu lực khi buổi đóng hoặc được mở lại. Nếu mở trang quá muộn hoặc hết hai phút, quét lại hay chọn Nhập PIN thay cho QR. Mã PIN dùng trong thời gian điểm danh còn mở. Khi triển khai qua Internet, cấu hình SOLOP_PUBLIC_ORIGIN thành địa chỉ HTTPS của ứng dụng. Máy chủ hiện vẫn cần được triển khai lên địa chỉ đó.

Camera trong website trên điện thoại thường cần HTTPS. Quét bằng Camera/Zalo bên ngoài để mở liên kết và nhập PIN không cần website truy cập camera.
Xem [nghiệp vụ và kiểm thử bàn giao](NANG-CAP-UNG-DUNG.md) để biết quy tắc chốt/mở lại, xin nghỉ, minh chứng, điều kiện dự thi, GPA, báo cáo và giới hạn triển khai.

Đăng nhập nhanh bằng nút tài khoản mẫu đã được bỏ. Mọi vai trò đều dùng biểu mẫu mã tài khoản và mật khẩu. Các mã và mật khẩu mẫu ở bảng trên phục vụ dữ liệu thử nghiệm.

## Kiểm thử

```powershell
mvn -f backend/pom.xml test
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
python scripts/verify-workflows.py
python scripts/verify-admin-onboarding.py
python scripts/verify-qr.py
python scripts/verify-migration.py
```

Backend chạy test trên CSDL `solop_enterprise_test`. Các script Python cần `playwright`, `pymysql` và Chrome; chạy tuần tự, dùng JDK 21 từ `JAVA_HOME` hoặc PATH. verify-workflows.py, verify-admin-onboarding.py và verify-qr.py tự tạo/xóa CSDL tạm `solop_verify_*` (cần tài khoản quản trị MySQL không mật khẩu; đổi qua `SOLOP_DB_ADMIN_USER` / `SOLOP_DB_ADMIN_PASSWORD`). verify-migration.py chỉ đọc CSDL thật và đối chiếu với bản backup SQLite. Xem tài liệu bàn giao để biết kết quả và phạm vi chưa kiểm chứng.

Mọi tài khoản có nút Đổi mật khẩu trong thanh điều hướng. Sinh viên tải minh chứng PDF/PNG/JPEG tối đa 2 MB trong form xin nghỉ. Tệp nằm trong CSDL và được sao lưu cùng dữ liệu. Sao lưu MySQL bằng `mysqldump` trước khi nâng cấp; giữ `backend/data/so-lop.db` làm bản rollback.
