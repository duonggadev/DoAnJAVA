# Sổ Lớp — Nghiệp vụ và kiểm thử bàn giao

Cập nhật ngày 25/11/2026. Ứng dụng Java 21 / Spring Boot / React chạy tại cổng 8098 / 5173, dùng CSDL MySQL 8. Đây là bản có thể trình diễn và sử dụng thử; các giới hạn triển khai thực tế nằm ở cuối tài liệu.

## Chức năng theo vai trò

| Vai trò | Luồng đã triển khai |
| --- | --- |
| Ban đào tạo | Dashboard theo học kỳ; quản lý tài khoản và khóa/mở khóa; cấp lại mật khẩu; tạo sinh viên kèm hồ sơ hoặc nhập hàng loạt từ CSV (báo lỗi từng dòng, hoàn tác toàn tệp khi có lỗi); CRUD môn, môn tiên quyết, trọng số; CRUD lớp, sĩ số và phân công giảng viên; thêm/gỡ sinh viên trong lớp kèm kiểm tra trùng lịch và sĩ số; cấu hình thời gian đăng ký và chuyên cần; xem kết quả; xuất CSV/Excel; mở lại bảng điểm kèm lý do |
| Giảng viên | Quản lý lịch từng buổi hoặc lặp 1–20 tuần; kiểm tra trùng lớp/phòng/giảng viên/sinh viên; mở, đóng, mở lại điểm danh trong ngày; QR động, PIN và trình chiếu; điều chỉnh chuyên cần; duyệt đơn nghỉ và tải minh chứng; nhập điểm trực tiếp hoặc Excel; chốt bảng điểm |
| Sinh viên | Đăng ký/hủy đăng ký đúng điều kiện; lịch cá nhân theo tuần; xin phép trước giờ học kèm tệp hoặc liên kết HTTPS; điểm danh qua QR/PIN; xem chuyên cần, điều kiện dự thi, kết quả đã chốt và GPA |
| Mọi tài khoản | Đăng nhập cá nhân, đăng xuất, đổi mật khẩu hiện tại; vô hiệu hóa các phiên cũ sau thay đổi thông tin xác thực |

## Quy tắc nghiệp vụ

1. API lấy danh tính từ phiên đăng nhập. Sinh viên không gửi mã người khác để điểm danh. QR mở từ Camera/Zalo yêu cầu đăng nhập lại trước khi xác nhận.
2. QR HMAC thay đổi mỗi 20 giây, chuyển tiếp 10 giây. Liên kết quét mới được giữ bằng chứng trong cùng phiên trình duyệt tối đa 120 giây để hoàn tất đăng nhập. Đóng/mở lại buổi sẽ vô hiệu mã và bằng chứng cũ. PIN tám số dùng trong cửa sổ mở điểm danh tối đa 10 phút.
3. Cookie thiết bị có chữ ký và HttpOnly; hash thiết bị theo buổi có ràng buộc duy nhất. Cùng cookie không được điểm danh cho nhiều sinh viên trong một buổi.
4. Đăng ký kiểm tra thời gian học kỳ, sĩ số, môn tiên quyết đã đạt, lịch sinh viên và lớp đã chốt. Một môn trong cùng học kỳ chỉ đăng ký một lớp. Hủy bị chặn khi đã có điểm, điểm danh, đơn xin phép hoặc buổi học đã kết thúc trong thời gian đăng ký.
5. Không tạo hoặc chuyển lịch vào khung giờ đã kết thúc. Không sửa/xóa buổi đã kết thúc, từng mở điểm danh, có điểm danh hoặc có đơn nghỉ. Các lượt vắng tự tính được giữ lại. Đổi giảng viên phụ trách phải không trùng lịch dạy.
6. Báo cáo chuyên cần tính buổi đã đóng điểm danh hoặc buổi chưa mở đã kết thúc theo múi giờ Việt Nam. Có mặt/đi muộn tính tham dự. Vắng có phép loại khỏi mẫu số. Buổi trước ngày đăng ký không tính cho đăng ký bổ sung.
7. Minh chứng nhận PDF/PNG/JPEG tối đa 2.000.000 byte, kiểm tra dấu hiệu định dạng. Tệp lưu cùng CSDL, tải xuống bằng API có kiểm tra người sở hữu/giảng viên phụ trách/ban đào tạo. Upload và tạo đơn cùng giao dịch; lỗi không để lại đơn hoặc tệp dở.
8. Điều kiện dự thi so sánh tỷ lệ chưa làm tròn với ngưỡng học kỳ, mặc định 80%. Chưa đạt ngưỡng thì chỉ lưu điểm quá trình, không nhập điểm thi. Khi không có buổi tính tỷ lệ thì coi là đủ điều kiện.
9. Khi chốt: phải hoàn tất các buổi và xử lý đơn chờ. Sinh viên đủ điều kiện cần đủ hai điểm thành phần. Sinh viên không đủ chuyên cần được lưu cờ cấm thi, tổng kết 0 và điểm chữ F. Quy tắc 0/F là lựa chọn của bản này, cần đối chiếu quy chế trường trước khi dùng kết quả chính thức.
10. Tổng kết của sinh viên đủ điều kiện = chuyên cần × trọng số + quá trình × trọng số + thi × trọng số. Điểm chuyên cần = 10 × tỷ lệ; không có buổi tính thì điểm này bằng 10. Trọng số môn tổng bằng 100; mặc định 10/30/60.
11. Điểm chữ: A ≥ 8.5; B+ ≥ 8; B ≥ 7; C+ ≥ 6.5; C ≥ 5.5; D+ ≥ 5; D ≥ 4; còn lại F. GPA hệ 4: 4/3.5/3/2.5/2/1.5/1/0; tính theo tín chỉ của tất cả lượt học đã chốt, kể cả học lại.
12. Chốt khóa điểm, lịch, chuyên cần và đơn nghỉ. Chỉ Ban đào tạo được mở lại, bắt buộc lý do. Mở lại xóa kết quả tổng kết/cờ cấm thi đã chốt, giữ điểm thành phần; giảng viên sửa và chốt lại. Lịch sử ghi người thực hiện, thời điểm, thao tác và lý do.
13. Không đổi tín chỉ hoặc trọng số môn khi còn lớp của môn đã chốt. Khóa ngoại bảo vệ tài khoản, lớp và môn đang có hồ sơ.
14. Excel nhập điểm dùng mẫu bốn cột đầu Ma SV / Ho ten / Qua trinh / Thi, nhận số trực tiếp 0–10 và tối đa hai chữ số thập phân. Không nhận công thức, boolean, mã trùng hoặc sinh viên ngoài lớp. Một dòng sai hoàn tác toàn bộ lần nhập. Tệp báo cáo tổng hợp có bố cục riêng và không dùng làm mẫu nhập.
15. Tạo sinh viên trong Ban đào tạo ghi hồ sơ trong cùng giao dịch khi có ít nhất một trường hồ sơ. Lớp hành chính phải tồn tại, trạng thái nằm trong DANG_HOC/BAO_LUU/THOI_HOC/TOT_NGHIEP, ngày sinh không ở tương lai và chương trình tốt nghiệp phải tồn tại. Tài khoản sinh viên mới nhận thông báo khởi tạo.
16. Nhập sinh viên CSV tối đa 500 dòng. Mã lớp/chương trình trong tệp được đối chiếu với danh mục trước khi gửi. Máy chủ kiểm tra từng dòng (hồ sơ, mật khẩu tối thiểu 8 ký tự, mã trùng trong tệp và mã đã tồn tại). Có một dòng lỗi thì không dòng nào được lưu; phản hồi trả số dòng và lý do.
17. Thêm sinh viên vào lớp kiểm tra tài khoản đang hoạt động, lớp chưa chốt điểm, sĩ số, trùng môn cùng học kỳ và trùng lịch từ ngày hiện tại. Gỡ bị chặn khi có điểm, điểm danh, đơn nghỉ hoặc buổi đã kết thúc trong thời gian đăng ký. Mỗi lần thêm/gỡ gửi thông báo cho sinh viên.

## Cách trình diễn

- Admin: tạo sinh viên kèm hồ sơ hoặc dùng Nhập sinh viên từ CSV (thử một dòng sai để xem hoàn tác) → tạo môn → lớp → cấu hình học kỳ và thời gian đăng ký → mở Danh sách sinh viên để thêm/gỡ và xem thông báo ở tài khoản sinh viên.
- Sinh viên: đăng ký lớp. Thử cùng môn khác lớp, trùng lịch hoặc quá sĩ số để xem lỗi.
- Giảng viên: tạo buổi hôm nay chưa kết thúc → mở điểm danh → trình chiếu.
- Điện thoại cùng Wi-Fi: quét bằng Camera/Zalo → đăng nhập sinh viên → kiểm tra họ tên/MSSV → xác nhận.
- Sinh viên: mở Lịch học và kết quả → chọn buổi sắp tới → gửi đơn kèm minh chứng.
- Giảng viên: xem kết quả buổi → tải minh chứng → duyệt/từ chối. Hoàn tất các buổi, nhập điểm rồi chốt.
- Admin: mở bảng điểm lớp → Mở lại bảng điểm → ghi lý do. Giảng viên điều chỉnh và chốt lại.
- Bất kỳ tài khoản: Đổi mật khẩu → nhập mật khẩu hiện tại và mật khẩu mới → đăng nhập lại. Phiên khác mất hiệu lực khi gọi API tiếp theo.

## Kiểm thử có thể chạy lại

Từ thư mục gốc:

```powershell
mvn -f backend/pom.xml test
mvn -f backend/pom.xml package -DskipTests
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
python scripts/verify-workflows.py
python scripts/verify-admin-onboarding.py
python scripts/verify-qr.py
python scripts/verify-migration.py
```

Các script trình duyệt cần Python, `playwright`, `pymysql` và Chrome. Cài bằng `python -m pip install playwright pymysql`. JAVA_HOME cần trỏ JDK 21 hoặc lệnh java trong PATH cần là Java 21. verify-workflows.py và verify-qr.py dùng chung cổng 8107/5184 nên chạy tuần tự; verify-admin-onboarding.py dùng 8109/5185. Mỗi script tự tạo và xóa CSDL MySQL tạm `solop_verify_*`, không đụng vào CSDL thật; cần tài khoản quản trị MySQL (mặc định root không mật khẩu, đổi qua `SOLOP_DB_ADMIN_USER` / `SOLOP_DB_ADMIN_PASSWORD`).

Kết quả ngày 25/11/2026 (đều chạy trên MySQL 8):

| Kiểm tra | Kết quả |
| --- | --- |
| Maven JUnit | 38 ca qua, 0 lỗi (CSDL `solop_enterprise_test`) |
| Node test bộ phân tích QR | 3 ca qua |
| Vite build | Qua |
| Trình duyệt/API nghiệp vụ | Qua: lịch định kỳ, xin nghỉ/duyệt, đăng ký, QR/PIN, điểm, Excel, chốt/mở lại, lịch sử, phân quyền minh chứng, đổi mật khẩu, thu hồi phiên, modal trên mobile |
| Quản lý sinh viên | Qua: tạo hồ sơ, nhập CSV có lỗi từng dòng, thông báo, giao diện 375/768/1440 |
| QR thực sự được sinh từ giao diện | Giải mã ảnh, mở liên kết, đăng nhập chậm 30 giây, kiểm tra đúng buổi và bản ghi điểm danh; ảnh QR, camera mô phỏng, PIN, mã cũ và tải lại liên kết đều qua |
| Chuyển dữ liệu SQLite sang MySQL | 20 bảng giữ nguyên số bản ghi, mã số và mật khẩu không đổi, không có bản ghi mồ côi, khóa ngoại được enforcing; đăng nhập và danh sách lớp qua API thật đều đọc được dữ liệu cũ |

## CSDL và vận hành

Ứng dụng dùng MySQL 8 với 20 bảng InnoDB và khóa ngoại thật. `schema-mysql.sql` tạo các bảng lõi; `InstitutionSchema`, `OperationsStore` và `ScheduleRules` bổ sung các bảng danh mục, thông báo/nhật ký và khóa lịch trình. Cấu hình mặc định trỏ tới `solop_enterprise` qua tài khoản `solop_app`; có thể đổi bằng `SOLOP_DB_URL`, `SOLOP_DB_USER`, `SOLOP_DB_PASSWORD`. Profile `sqlite` chỉ để rollback, đọc `backend/data/so-lop.db`.

Chuyển dữ liệu từ bản cũ: `python scripts/migrate-sqlite-to-mysql.py --truncate`, sau đó `python scripts/verify-migration.py` để đối chiếu. Tệp SQLite gốc không bị sửa và được giữ làm bản rollback. Trước khi nâng cấp CSDL thật cần sao lưu bằng `mysqldump` (bao gồm cả tệp minh chứng nằm trong bảng `minh_chung`).

Profile prod dùng MySQL, cookie Secure. Biến môi trường: DATABASE_URL (JDBC MySQL), DATABASE_USERNAME, DATABASE_PASSWORD, SOLOP_DEVICE_KEY (Base64 URL của ít nhất 32 byte ngẫu nhiên), SOLOP_ADMIN_PASSWORD cho lần khởi tạo đầu (từ 12 ký tự). Đặt SOLOP_PUBLIC_ORIGIN của frontend thành origin HTTPS thực tế khi build.

## Giới hạn đã xác định

- Chỉ kiểm thử tích hợp với MySQL 8.0.30 (Laragon) trên Windows. PostgreSQL/Docker chưa được kiểm chứng cho bản này.
- Camera vật lý và tải đồng thời nhiều người dùng chưa được đo. Script camera dùng video mô phỏng trong Chrome.
- Cookie không định danh chắc chắn thiết bị vật lý; đổi trình duyệt/xóa cookie có thể vượt giới hạn. QR còn hiệu lực có thể bị chia sẻ.
- Cập nhật điểm danh dùng polling 4 giây. Rate limit và phiên nằm trong một tiến trình; triển khai nhiều instance cần kho dùng chung.
- GPA, chính sách cấm thi 0/F và cách xử lý vắng có phép là quy ước đã mô tả, cần khớp quy chế trường.
- Các danh mục được tải về để lọc/phân trang trên giao diện. Chưa đo dung lượng hoặc hiệu năng với dữ liệu toàn trường.
