package vn.edu.solop;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static vn.edu.solop.AdminController.*;

@RestController
@RequestMapping("/api")
class AcademicController {
    static final String METRICS = """
        SELECT d.id AS registration_id, d.lop_hoc_id AS class_id, d.sinh_vien_id AS student_id,
            COUNT(CASE WHEN b.id IS NOT NULL AND (a.trang_thai IS NULL OR a.trang_thai<>'CO_PHEP') THEN 1 END) AS total,
            COUNT(CASE WHEN a.trang_thai IN ('CO_MAT','DI_MUON') THEN 1 END) AS attended,
            COUNT(CASE WHEN b.id IS NOT NULL AND (a.trang_thai IS NULL OR a.trang_thai='VANG') THEN 1 END) AS absent,
            COUNT(CASE WHEN a.trang_thai='CO_PHEP' THEN 1 END) AS excused
        FROM dang_ky d
        LEFT JOIN buoi_hoc b ON b.lop_hoc_id=d.lop_hoc_id
          AND (d.ngay_dang_ky IS NULL OR b.ngay_hoc>=d.ngay_dang_ky)
          AND (b.han_diem_danh<=? OR (b.han_diem_danh IS NULL AND (concat(b.ngay_hoc,'T',substr(concat(b.gio_ket_thuc,':00'),1,8)))<=?))
        LEFT JOIN diem_danh a ON a.buoi_hoc_id=b.id AND a.sinh_vien_id=d.sinh_vien_id
        GROUP BY d.id,d.lop_hoc_id,d.sinh_vien_id
        """;

    private final JdbcTemplate db;
    private final OperationsStore operations;
    private final DatabaseDialect dialect;
    private final Clock clock;
    private final AttendanceController attendance;

    AcademicController(JdbcTemplate db, Clock clock, AttendanceController attendance,DatabaseDialect dialect,OperationsStore operations) {
        this.operations=operations;
        this.dialect=dialect;
        this.db = db;
        this.clock = clock;
        this.attendance = attendance;
    }

    @GetMapping("/admin/attendance")
    Map<String, Object> attendanceOverview(@RequestParam String term) {
        if (!term.matches("[0-9]{4}-[1-3]")) throw bad("Học kỳ chưa hợp lệ.");
        var rows = db.queryForList("WITH metrics AS (" + METRICS + ") " + """
                SELECT u.ma_so AS code,u.ho_ten AS name,l.ma_lop AS "classCode",m.total,m.attended,m.absent,m.excused,
                    COALESCE(h.nguong_chuyen_can,80) AS threshold
                FROM metrics m JOIN lop_hoc l ON l.id=m.class_id JOIN nguoi_dung u ON u.id=m.student_id
                LEFT JOIN hoc_ky h ON h.ma=l.hoc_ky
                WHERE l.hoc_ky=? AND m.total>0 ORDER BY u.ma_so,l.ma_lop
                """, clock.millis(), cutoff(clock), term);
        long total = rows.stream().mapToLong(row -> number(row,"total")).sum();
        long attended = rows.stream().mapToLong(row -> number(row,"attended")).sum();
        var alerts = rows.stream().filter(row -> number(row,"attended") * 100 < number(row,"total") * number(row,"threshold")).toList();
        return Map.of("total", total, "attended", attended, "alerts", alerts.stream().limit(100).toList(), "alertCount", alerts.size());
    }

    @GetMapping("/my-study")
    Map<String, Object> studentStudy(@RequestParam String week, Principal actor) {
        LocalDate from;
        try { from = LocalDate.parse(week); }
        catch (java.time.DateTimeException error) { throw bad("Tuần học chưa hợp lệ."); }
        if (from.getYear()<1900 || from.getYear()>2200) throw bad("Tuần học chưa hợp lệ.");
        var sessions = db.queryForList("""
                SELECT b.id,b.ngay_hoc AS date,b.gio_bat_dau AS start,b.gio_ket_thuc AS end,b.phong AS room,
                    b.noi_dung AS topic,l.ma_lop AS code,m.ten_mon AS subject,
                    a.trang_thai AS status,x.trang_thai AS "requestStatus"
                FROM nguoi_dung u JOIN dang_ky d ON d.sinh_vien_id=u.id JOIN lop_hoc l ON l.id=d.lop_hoc_id
                JOIN mon_hoc m ON m.id=l.mon_hoc_id JOIN buoi_hoc b ON b.lop_hoc_id=l.id
                LEFT JOIN diem_danh a ON a.buoi_hoc_id=b.id AND a.sinh_vien_id=u.id
                LEFT JOIN don_xin_phep x ON x.buoi_hoc_id=b.id AND x.sinh_vien_id=u.id
                WHERE u.ma_so=? AND b.ngay_hoc BETWEEN ? AND ? AND (d.ngay_dang_ky IS NULL OR b.ngay_hoc>=d.ngay_dang_ky)
                ORDER BY b.ngay_hoc,b.gio_bat_dau,b.id
                """, actor.getName(), from.toString(), from.plusDays(6).toString());
        var grades = db.queryForList("""
                SELECT m.ten_mon AS subject,m.so_tin_chi AS credits,l.ma_lop AS code,l.hoc_ky AS term,
                    d.diem_tong_ket AS total,d.diem_chu AS letter,d.bi_cam_thi AS barred
                FROM dang_ky d JOIN lop_hoc l ON l.id=d.lop_hoc_id JOIN mon_hoc m ON m.id=l.mon_hoc_id
                JOIN nguoi_dung u ON u.id=d.sinh_vien_id
                WHERE u.ma_so=? AND l.da_chot_diem=1 AND d.diem_tong_ket IS NOT NULL ORDER BY l.hoc_ky DESC,l.ma_lop
                """, actor.getName());
        long credits = grades.stream().mapToLong(row -> number(row,"credits")).sum();
        double total10 = grades.stream().mapToDouble(row -> ((Number)row.get("total")).doubleValue() * number(row,"credits")).sum();
        double total4 = grades.stream().mapToDouble(row -> gradePoint(row.get("letter").toString()) * number(row,"credits")).sum();
        var alerts = db.queryForList("WITH metrics AS (" + METRICS + ") " + """
                SELECT l.ma_lop AS code,s.ten_mon AS subject,m.total,m.attended,COALESCE(h.nguong_chuyen_can,80) AS threshold
                FROM metrics m JOIN lop_hoc l ON l.id=m.class_id JOIN mon_hoc s ON s.id=l.mon_hoc_id
                JOIN nguoi_dung u ON u.id=m.student_id LEFT JOIN hoc_ky h ON h.ma=l.hoc_ky
                WHERE u.ma_so=? AND m.total>0 AND m.attended*100<m.total*COALESCE(h.nguong_chuyen_can,80) ORDER BY l.ma_lop
                """, clock.millis(), cutoff(clock), actor.getName());
        return Map.of("sessions", sessions, "grades", grades, "alerts", alerts, "credits", credits,
                "gpa10", credits > 0 ? round(total10/credits) : "–", "gpa4", credits > 0 ? round(total4/credits) : "–");
    }

    @PostMapping("/classes/{id}/finalize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void finalizeGrades(@PathVariable long id, Principal actor) {
        updated(db.update("""
                UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=? AND giang_vien_id=(SELECT id FROM nguoi_dung WHERE ma_so=?)
                """, id, actor.getName()));
        var weights = db.queryForMap("""
                SELECT l.da_chot_diem,s.ty_le_chuyen_can,s.ty_le_qua_trinh,s.ty_le_thi
                FROM lop_hoc l JOIN mon_hoc s ON s.id=l.mon_hoc_id WHERE l.id=?
                """, id);
        if (number(weights,"da_chot_diem") != 0) return;
        if (db.queryForObject("""
                SELECT COUNT(*) FROM buoi_hoc WHERE lop_hoc_id=? AND
                    (han_diem_danh>? OR (han_diem_danh IS NULL AND concat(ngay_hoc,'T',substr(concat(gio_ket_thuc,':00'),1,8))>?))
                """, Integer.class, id, clock.millis(), cutoff(clock)) != 0) throw conflict("Còn buổi chưa hoàn tất.");
        if(db.queryForObject("""
                SELECT COUNT(*) FROM don_xin_phep x JOIN buoi_hoc b ON b.id=x.buoi_hoc_id
                WHERE b.lop_hoc_id=? AND x.trang_thai='CHO_DUYET'
                """,Integer.class,id)>0) throw conflict("Cần xử lý các đơn xin phép đang chờ trước khi chốt điểm.");
        var rows = db.queryForList("WITH metrics AS (" + METRICS + ") " + """
                SELECT d.id,d.diem_qua_trinh,d.diem_thi,m.total,m.attended,COALESCE(h.nguong_chuyen_can,80) AS threshold FROM metrics m
                JOIN dang_ky d ON d.id=m.registration_id JOIN lop_hoc l ON l.id=d.lop_hoc_id
                LEFT JOIN hoc_ky h ON h.ma=l.hoc_ky WHERE m.class_id=?
                """, clock.millis(), cutoff(clock), id);
        if (rows.isEmpty()) throw conflict("Lớp chưa có sinh viên để chốt điểm.");
        for (var row : rows) {
            boolean barred=number(row,"total")>0 && number(row,"attended")*100<number(row,"total")*number(row,"threshold");
            if(!barred && (row.get("diem_qua_trinh")==null || row.get("diem_thi")==null)) {
                throw conflict("Cần nhập đủ điểm quá trình và điểm thi trước khi chốt.");
            }
            if(barred){
                db.update("UPDATE dang_ky SET diem_tong_ket=0,diem_chu='F',bi_cam_thi=1 WHERE id=?",row.get("id"));
                continue;
            }
            long total = number(row,"total");
            double attendanceScore = total == 0 ? 10 : number(row,"attended") * 10.0 / total;
            BigDecimal score = round((attendanceScore * number(weights,"ty_le_chuyen_can")
                    + ((Number)row.get("diem_qua_trinh")).doubleValue() * number(weights,"ty_le_qua_trinh")
                    + ((Number)row.get("diem_thi")).doubleValue() * number(weights,"ty_le_thi"))/100);
            db.update("UPDATE dang_ky SET diem_tong_ket=?,diem_chu=?,bi_cam_thi=0 WHERE id=?", score, gradeLetter(score.doubleValue()), row.get("id"));
        }
        db.update("UPDATE lop_hoc SET da_chot_diem=1 WHERE id=?", id);
        operations.notifyClass(id,"Bảng điểm đã chốt","Kết quả học phần đã được chốt. Mở Lịch học và kết quả để xem điểm.");
        db.update("""
                INSERT INTO nhat_ky_diem(id,lop_hoc_id,nguoi_dung_id,hanh_dong,ly_do,thoi_gian)
                SELECT ?,?,id,'CHOT_DIEM','Giảng viên chốt bảng điểm',? FROM nguoi_dung WHERE ma_so=?
                """,java.util.UUID.randomUUID().toString(),id,clock.millis(),actor.getName());
    }

    @PostMapping("/admin/classes/{id}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void reopenGrades(@PathVariable long id,@RequestBody ReopenInput input,Principal actor) {
        String reason=required(input.reason(),500,"Lý do mở lại");
        updated(db.update("UPDATE lop_hoc SET da_chot_diem=0 WHERE id=? AND da_chot_diem=1",id));
        db.update("UPDATE dang_ky SET diem_tong_ket=NULL,diem_chu=NULL,bi_cam_thi=0 WHERE lop_hoc_id=?",id);
        operations.notifyClass(id,"Bảng điểm được mở lại","Ban đào tạo đang đối soát kết quả. Điểm tổng kết sẽ hiển thị lại sau khi giảng viên chốt.");
        db.update("""
                INSERT INTO nhat_ky_diem(id,lop_hoc_id,nguoi_dung_id,hanh_dong,ly_do,thoi_gian)
                SELECT ?,?,id,'MO_LAI',?,? FROM nguoi_dung WHERE ma_so=?
                """,java.util.UUID.randomUUID().toString(),id,reason,clock.millis(),actor.getName());
    }

    @GetMapping("/classes/{id}/grade-history")
    List<Map<String,Object>> gradeHistory(@PathVariable long id,Principal actor) {
        var account=attendance.me(actor);
        if("SINH_VIEN".equals(account.role()))throw missing();
        attendance.requireClass(id,account);
        return db.queryForList("""
                SELECT n.id,n.hanh_dong AS action,n.ly_do AS reason,n.thoi_gian AS time,u.ho_ten AS actor
                FROM nhat_ky_diem n JOIN nguoi_dung u ON u.id=n.nguoi_dung_id
                WHERE n.lop_hoc_id=? ORDER BY n.thoi_gian DESC,n.id
                """,id);
    }

    record ReopenInput(String reason){}

    @PostMapping("/sessions/{id}/leave")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    void requestLeave(@PathVariable long id, @RequestBody LeaveInput input, Principal actor) {
        updated(db.update("""
                UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=(SELECT lop_hoc_id FROM buoi_hoc WHERE id=?)
                  AND da_chot_diem=0 AND EXISTS(SELECT 1 FROM dang_ky d JOIN nguoi_dung u ON u.id=d.sinh_vien_id
                    WHERE d.lop_hoc_id=lop_hoc.id AND u.ma_so=?)
                """, id, actor.getName()));
        String reason = required(input.reason(), 500, "Lý do");
        String evidence = input.evidence() == null || input.evidence().isBlank() ? null : required(input.evidence(), 512, "Liên kết minh chứng");
        if (evidence != null) validateEvidence(evidence);
        var session = db.queryForList("""
                SELECT b.ngay_hoc,b.gio_bat_dau,d.sinh_vien_id FROM buoi_hoc b
                JOIN dang_ky d ON d.lop_hoc_id=b.lop_hoc_id JOIN nguoi_dung s ON s.id=d.sinh_vien_id
                JOIN lop_hoc l ON l.id=b.lop_hoc_id
                WHERE b.id=? AND s.ma_so=? AND l.da_chot_diem=0 AND (d.ngay_dang_ky IS NULL OR b.ngay_hoc>=d.ngay_dang_ky)
                """, id, actor.getName()).stream().findFirst().orElseThrow(AdminController::missing);
        if (!LocalDateTime.now(clock).isBefore(LocalDateTime.parse(session.get("ngay_hoc") + "T" + session.get("gio_bat_dau")))) {
            throw conflict("Đơn xin phép phải gửi trước giờ học.");
        }
        db.update("""
                INSERT INTO don_xin_phep(buoi_hoc_id,sinh_vien_id,ly_do,minh_chung,trang_thai)
                VALUES (?,?,?,?,'CHO_DUYET')
                """, id, session.get("sinh_vien_id"), reason, evidence);
        operations.notifyTeacher(id,"Đơn xin nghỉ mới","Có đơn xin nghỉ cần xử lý. Mở kết quả điểm danh của buổi học để xem đơn và minh chứng.");
    }

    @PutMapping("/leave/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void reviewLeave(@PathVariable long id, @RequestBody LeaveDecision decision, Principal actor) {
        if (decision.approve() == null) throw bad("Chọn duyệt hoặc từ chối.");
        updated(db.update("""
                UPDATE lop_hoc SET ma_lop=ma_lop WHERE giang_vien_id=(SELECT id FROM nguoi_dung WHERE ma_so=?)
                    AND id=(SELECT b.lop_hoc_id FROM buoi_hoc b JOIN don_xin_phep x ON x.buoi_hoc_id=b.id WHERE x.id=?)
                    AND da_chot_diem=0
                """, actor.getName(), id));
        var row = db.queryForMap("SELECT buoi_hoc_id,sinh_vien_id,trang_thai FROM don_xin_phep WHERE id=?", id);
        if (!"CHO_DUYET".equals(row.get("trang_thai"))) throw conflict("Đơn đã được xử lý.");
        db.update("UPDATE don_xin_phep SET trang_thai=?,nguoi_duyet_id=(SELECT id FROM nguoi_dung WHERE ma_so=?) WHERE id=?",
                decision.approve() ? "DA_DUYET" : "TU_CHOI", actor.getName(), id);
        operations.notifyUser(((Number)row.get("sinh_vien_id")).longValue(),"Kết quả đơn xin nghỉ",decision.approve()?"Đơn xin nghỉ của bạn đã được duyệt.":"Đơn xin nghỉ của bạn đã bị từ chối. Liên hệ giảng viên nếu cần bổ sung thông tin.");
        if (decision.approve()) {
            db.update(dialect.choose("""
                    INSERT INTO diem_danh(buoi_hoc_id,sinh_vien_id,trang_thai,ghi_chu) VALUES (?,?,'CO_PHEP','Đơn xin phép đã duyệt')
                    ON CONFLICT(buoi_hoc_id,sinh_vien_id) DO UPDATE SET trang_thai=excluded.trang_thai,ghi_chu=excluded.ghi_chu
                    WHERE diem_danh.trang_thai='VANG'
                    ""","""
                    INSERT INTO diem_danh(buoi_hoc_id,sinh_vien_id,trang_thai,ghi_chu) VALUES (?,?,'CO_PHEP','Đơn xin phép đã duyệt')
                    ON DUPLICATE KEY UPDATE
                      ghi_chu=IF(trang_thai='VANG',VALUES(ghi_chu),ghi_chu),
                      trang_thai=IF(trang_thai='VANG',VALUES(trang_thai),trang_thai)
                    """), row.get("buoi_hoc_id"), row.get("sinh_vien_id"));
        }
    }

    @GetMapping("/classes/{id}/exports/{kind}")
    ResponseEntity<byte[]> export(@PathVariable long id, @PathVariable String kind, @RequestParam(defaultValue="csv") String format, Principal actor) throws java.io.IOException {
        var account = attendance.me(actor);
        if ("SINH_VIEN".equals(account.role())) throw missing();
        attendance.requireClass(id, account);
        List<String> columns;
        List<Map<String, Object>> rows;
        switch (kind) {
            case "grades", "roster" -> {
                columns = kind.equals("grades") ? List.of("Mã SV","Họ tên","Điểm quá trình","Điểm thi","Tổng kết","Điểm chữ","Cấm thi")
                        : List.of("Mã SV","Họ tên");
                rows = db.queryForList("""
                        SELECT s.ma_so,s.ho_ten,d.diem_qua_trinh,d.diem_thi,d.diem_tong_ket,d.diem_chu,d.bi_cam_thi
                        FROM dang_ky d JOIN nguoi_dung s ON s.id=d.sinh_vien_id WHERE d.lop_hoc_id=? ORDER BY s.ma_so
                        """, id);
            }
            case "attendance" -> {
                columns = List.of("Mã SV","Họ tên","Tham dự","Vắng","Có phép","Số buổi tính tỷ lệ");
                rows = db.queryForList("WITH metrics AS (" + METRICS + ") " + """
                        SELECT u.ma_so,u.ho_ten,m.attended,m.absent,m.excused,m.total
                        FROM metrics m JOIN nguoi_dung u ON u.id=m.student_id WHERE m.class_id=? ORDER BY u.ma_so
                        """, clock.millis(), cutoff(clock), id);
            }
            default -> throw missing();
        }
        if("xlsx".equals(format)) {
            try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var bytes=new java.io.ByteArrayOutputStream()) {
                var sheet=book.createSheet("So Lop");
                var header=sheet.createRow(0);
                for(int i=0;i<columns.size();i++)header.createCell(i).setCellValue(columns.get(i));
                int index=1;
                for(var item:rows) {
                    var row=sheet.createRow(index++);
                    int column=0;
                    for(var cell:item.values().stream().limit(columns.size()).toList()) {
                        var value=row.createCell(column++);
                        if(cell instanceof Number number)value.setCellValue(number.doubleValue());
                        else if(cell!=null)value.setCellValue(cell.toString());
                    }
                }
                for(int i=0;i<columns.size();i++)sheet.autoSizeColumn(i);
                book.write(bytes);
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=so-lop-"+id+"-"+kind+".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes.toByteArray());
            }
        }
        if(!"csv".equals(format))throw bad("Chọn định dạng csv hoặc xlsx.");
        StringBuilder csv = new StringBuilder("\uFEFF").append(String.join(",",columns)).append("\r\n");
        for (var row : rows) {
            csv.append(row.values().stream().limit(columns.size()).map(AcademicController::csvCell)
                    .collect(Collectors.joining(","))).append("\r\n");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=so-lop-" + id + "-" + kind + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String cutoff(Clock clock) {
        return LocalDateTime.now(clock).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    private static String csvCell(Object cell) {
        String value = cell == null ? "" : cell.toString();
        if (!(cell instanceof Number) && value.stripLeading().matches("(?s)^[=+@-].*")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static long number(Map<String, Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    private static BigDecimal round(double number) { return BigDecimal.valueOf(number).setScale(2, RoundingMode.HALF_UP); }

    static String gradeLetter(double grade) {
        if (grade >= 8.5) return "A";
        if (grade >= 8) return "B+";
        if (grade >= 7) return "B";
        if (grade >= 6.5) return "C+";
        if (grade >= 5.5) return "C";
        if (grade >= 5) return "D+";
        if (grade >= 4) return "D";
        return "F";
    }

    private static double gradePoint(String letter) {
        return switch (letter) {
            case "A" -> 4; case "B+" -> 3.5; case "B" -> 3; case "C+" -> 2.5;
            case "C" -> 2; case "D+" -> 1.5; case "D" -> 1; default -> 0;
        };
    }

    private static void validateEvidence(String evidence) {
        try {
            URI uri = URI.create(evidence);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null) return;
        } catch (IllegalArgumentException ignored) {
            // Fall through to the localized validation error.
        }
        throw bad("Minh chứng phải là liên kết HTTPS hợp lệ.");
    }

    record LeaveInput(String reason, String evidence) {}
    record LeaveDecision(Boolean approve) {}
}