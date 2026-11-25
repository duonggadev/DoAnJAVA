package vn.edu.solop;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static vn.edu.solop.AdminController.*;

@RestController
@RequestMapping("/api")
class LearningController {
    private final JdbcTemplate db;
    private final StudentEligibility eligibility;
    private final OperationsStore operations;
    private final DatabaseDialect dialect;
    private final Clock clock;

    private final boolean requireTerm;
    private final ScheduleRules schedule;
    private final GradePolicy gradePolicy;

    LearningController(JdbcTemplate db, Clock clock, @Value("${solop.require-term:false}") boolean requireTerm, ScheduleRules schedule, GradePolicy gradePolicy,DatabaseDialect dialect,StudentEligibility eligibility,OperationsStore operations) {
        this.eligibility=eligibility;
        this.operations=operations;
        this.dialect=dialect;
        this.db = db;
        this.clock = clock;
        this.requireTerm = requireTerm;
        this.schedule=schedule;
        this.gradePolicy=gradePolicy;
    }

    @GetMapping("/enrollment")
    List<Map<String, Object>> enrollment(Principal actor) {
        return db.queryForList("""
                SELECT l.id, l.ma_lop AS code, l.hoc_ky AS term, m.ten_mon AS subject,
                       u.ho_ten AS teacher, m.so_tin_chi AS credits,
                       CASE WHEN EXISTS(SELECT 1 FROM dang_ky WHERE lop_hoc_id=l.id AND sinh_vien_id=s.id) THEN 1 ELSE 0 END AS enrolled,
                       CASE WHEN EXISTS(SELECT 1 FROM dang_ky other JOIN lop_hoc section ON section.id=other.lop_hoc_id
                           WHERE other.sinh_vien_id=s.id AND section.mon_hoc_id=l.mon_hoc_id AND section.hoc_ky=l.hoc_ky AND section.id<>l.id)
                           THEN 1 ELSE 0 END AS "sameSubject",
                       CASE WHEN m.mon_tien_quyet_id IS NOT NULL AND NOT EXISTS(
                           SELECT 1 FROM dang_ky passed JOIN lop_hoc prerequisite ON prerequisite.id=passed.lop_hoc_id
                           WHERE passed.sinh_vien_id=s.id AND prerequisite.mon_hoc_id=m.mon_tien_quyet_id
                           AND prerequisite.da_chot_diem=1 AND passed.diem_tong_ket>=4)
                           THEN 1 ELSE 0 END AS "missingPrerequisite",
                       CASE WHEN EXISTS(SELECT 1 FROM ho_so_sinh_vien h WHERE h.sinh_vien_id=s.id AND h.trang_thai<>'DANG_HOC') THEN 0 ELSE 1 END AS "studentActive",
                       l.si_so_toi_da AS capacity, (SELECT COUNT(*) FROM dang_ky WHERE lop_hoc_id=l.id) AS enrollment,
                       CASE WHEN l.da_chot_diem=1 THEN 0 WHEN h.ma IS NULL THEN ? WHEN ? BETWEEN h.mo_dang_ky AND h.dong_dang_ky THEN 1 ELSE 0 END AS "registrationOpen",
                       CASE WHEN EXISTS (
                           SELECT 1 FROM dang_ky d JOIN buoi_hoc x ON x.lop_hoc_id=d.lop_hoc_id
                           JOIN buoi_hoc y ON y.lop_hoc_id=l.id AND y.ngay_hoc=x.ngay_hoc
                             AND substr(concat(y.gio_bat_dau,':00'),1,8)<substr(concat(x.gio_ket_thuc,':00'),1,8)
                             AND substr(concat(x.gio_bat_dau,':00'),1,8)<substr(concat(y.gio_ket_thuc,':00'),1,8)
                           WHERE d.sinh_vien_id=s.id AND d.lop_hoc_id<>l.id AND y.ngay_hoc>=?
                       ) THEN 1 ELSE 0 END AS "scheduleConflict"
                FROM lop_hoc l JOIN mon_hoc m ON m.id=l.mon_hoc_id
                JOIN nguoi_dung u ON u.id=l.giang_vien_id
                JOIN nguoi_dung s ON s.ma_so=? AND s.vai_tro='SINH_VIEN'
                LEFT JOIN hoc_ky h ON h.ma=l.hoc_ky
                ORDER BY l.hoc_ky DESC, l.ma_lop
                """, requireTerm ? 0 : 1, LocalDate.now(clock).toString(), LocalDate.now(clock).toString(), actor.getName());
    }

    @PostMapping("/enrollment/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void register(@PathVariable long id, Principal actor) {
        lockRegistration(id, actor);
        eligibility.requireActive(actor.getName());
        if (db.queryForObject("""
                SELECT COUNT(*) FROM dang_ky d JOIN nguoi_dung u ON u.id=d.sinh_vien_id
                WHERE d.lop_hoc_id=? AND u.ma_so=?
                """, Integer.class, id, actor.getName()) > 0) return;
        checkTerm(id);
        var available = enrollment(actor).stream().filter(row -> ((Number)row.get("id")).longValue() == id)
                .findFirst().orElseThrow(AdminController::missing);
        if (((Number)available.get("sameSubject")).intValue()!=0)throw conflict("Bạn đã đăng ký một lớp khác của cùng môn trong học kỳ.");
        if (((Number)available.get("capacity")).longValue() <= ((Number)available.get("enrollment")).longValue()) {
            throw conflict("Lớp đã đủ sĩ số.");
        }
        if (((Number)available.get("scheduleConflict")).intValue() != 0) throw conflict("Lớp trùng lịch học của bạn.");
        int missingRequirement = db.queryForObject("""
                SELECT COUNT(*) FROM lop_hoc l JOIN mon_hoc m ON m.id=l.mon_hoc_id WHERE l.id=?
                AND m.mon_tien_quyet_id IS NOT NULL AND NOT EXISTS (
                    SELECT 1 FROM dang_ky d JOIN lop_hoc p ON p.id=d.lop_hoc_id
                    JOIN nguoi_dung u ON u.id=d.sinh_vien_id WHERE u.ma_so=? AND p.mon_hoc_id=m.mon_tien_quyet_id
                      AND p.da_chot_diem=1 AND d.diem_tong_ket>=4
                )
                """, Integer.class, id, actor.getName());
        if (missingRequirement != 0) throw conflict("Bạn chưa đạt môn tiên quyết.");
        if (db.queryForObject("SELECT da_chot_diem FROM lop_hoc WHERE id=?", Integer.class, id) != 0) {
            throw conflict("Lớp đã chốt điểm.");
        }
        updated(db.update("""
                INSERT INTO dang_ky(lop_hoc_id,sinh_vien_id,ngay_dang_ky)
                SELECT l.id,s.id,? FROM lop_hoc l JOIN nguoi_dung s ON s.ma_so=? AND s.vai_tro='SINH_VIEN'
                WHERE l.id=?
                """, LocalDate.now(clock).toString(), actor.getName(), id));
        operations.notifyUser(db.queryForObject("SELECT id FROM nguoi_dung WHERE ma_so=?",Long.class,actor.getName()),"Đăng ký học phần","Bạn đã đăng ký lớp học phần. Xem lịch học để theo dõi buổi học.");
    }

    private void lockRegistration(long id, Principal actor) {
        schedule.lock();
        updated(db.update("UPDATE nguoi_dung SET ban_phien=ban_phien WHERE ma_so=? AND bi_khoa=0", actor.getName()));
        updated(db.update("UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=?", id));
    }

    private void checkTerm(long id) {
        db.update("UPDATE hoc_ky SET ma=ma WHERE ma=(SELECT hoc_ky FROM lop_hoc WHERE id=?)", id);
        var dates = db.queryForList("""
                SELECT h.mo_dang_ky,h.dong_dang_ky FROM hoc_ky h JOIN lop_hoc l ON l.hoc_ky=h.ma WHERE l.id=?
                """, id);
        if (dates.isEmpty() && !requireTerm) return;
        if (!dates.isEmpty()) {
            String now = LocalDate.now(clock).toString();
            var term = dates.getFirst();
            if (now.compareTo(term.get("mo_dang_ky").toString()) >= 0 && now.compareTo(term.get("dong_dang_ky").toString()) <= 0) return;
        }
        throw conflict("Cổng đăng ký học kỳ đang đóng.");
    }

    @DeleteMapping("/enrollment/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void withdraw(@PathVariable long id, Principal actor) {
        lockRegistration(id, actor);
        checkTerm(id);
        if(db.queryForObject("SELECT da_chot_diem FROM lop_hoc WHERE id=?",Integer.class,id)!=0)throw conflict("Lớp đã chốt điểm.");
        var record = db.queryForList("""
                SELECT d.id, d.diem_qua_trinh, d.diem_thi,
                  CASE WHEN EXISTS(SELECT 1 FROM diem_danh a JOIN buoi_hoc b ON b.id=a.buoi_hoc_id
                      WHERE b.lop_hoc_id=d.lop_hoc_id AND a.sinh_vien_id=d.sinh_vien_id)
                  OR EXISTS(SELECT 1 FROM don_xin_phep x JOIN buoi_hoc b ON b.id=x.buoi_hoc_id
                      WHERE b.lop_hoc_id=d.lop_hoc_id AND x.sinh_vien_id=d.sinh_vien_id) THEN 1 ELSE 0 END AS "hasAttendance",
                  CASE WHEN EXISTS(SELECT 1 FROM buoi_hoc b WHERE b.lop_hoc_id=d.lop_hoc_id
                      AND (d.ngay_dang_ky IS NULL OR b.ngay_hoc>=d.ngay_dang_ky)
                      AND (b.han_diem_danh<=? OR (b.han_diem_danh IS NULL AND (concat(b.ngay_hoc,'T',substr(concat(b.gio_ket_thuc,':00'),1,8)))<=?))) THEN 1 ELSE 0 END AS completed
                FROM dang_ky d JOIN nguoi_dung s ON s.id=d.sinh_vien_id
                WHERE s.ma_so=? AND d.lop_hoc_id=?
                """, clock.millis(), AcademicController.cutoff(clock), actor.getName(), id).stream().findFirst().orElseThrow(AdminController::missing);
        if (record.get("diem_qua_trinh") != null || record.get("diem_thi") != null
                || ((Number)record.get("hasAttendance")).intValue() != 0 || ((Number)record.get("completed")).intValue() != 0) {
            throw conflict("Không thể hủy đăng ký khi lớp đã có điểm, kết quả điểm danh hoặc buổi học đã kết thúc.");
        }
        updated(db.update("DELETE FROM dang_ky WHERE id=?", record.get("id")));
    }

    @GetMapping("/classes/{id}/grades")
    List<Map<String, Object>> grades(@PathVariable long id, Principal actor) {
        return db.queryForList("WITH metrics AS ("+AcademicController.METRICS+") "+"""
                SELECT d.id, s.ma_so AS code, s.ho_ten AS name,
                       d.diem_qua_trinh AS coursework, d.diem_thi AS exam, d.diem_tong_ket AS total, d.diem_chu AS letter,
                       l.da_chot_diem AS finalized,
                       CASE WHEN l.da_chot_diem=1 THEN 1-d.bi_cam_thi
                         WHEN m.total=0 OR m.attended*100>=m.total*COALESCE(h.nguong_chuyen_can,80) THEN 1 ELSE 0 END AS eligible
                FROM dang_ky d JOIN lop_hoc l ON l.id=d.lop_hoc_id
                JOIN nguoi_dung s ON s.id=d.sinh_vien_id JOIN nguoi_dung actor ON actor.ma_so=?
                JOIN metrics m ON m.registration_id=d.id LEFT JOIN hoc_ky h ON h.ma=l.hoc_ky
                WHERE l.id=? AND (actor.id=l.giang_vien_id OR actor.id=d.sinh_vien_id OR actor.vai_tro='ADMIN')
                ORDER BY s.ma_so
                """, clock.millis(),AcademicController.cutoff(clock),actor.getName(), id);
    }

    @PutMapping("/registrations/{id}/grades")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void editGrades(@PathVariable long id, @RequestBody Grades grades, Principal actor) {
        updated(db.update("""
                UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=(SELECT lop_hoc_id FROM dang_ky WHERE id=?)
                    AND giang_vien_id=(SELECT id FROM nguoi_dung WHERE ma_so=?)
                """, id, actor.getName()));
        if (db.queryForObject("""
                SELECT l.da_chot_diem FROM dang_ky d JOIN lop_hoc l ON l.id=d.lop_hoc_id WHERE d.id=?
                """, Integer.class, id) != 0) throw conflict("Bảng điểm đã chốt.");
        validateGrade(grades.coursework());
        validateGrade(grades.exam());
        if(grades.exam()!=null && !gradePolicy.eligible(id))throw conflict("Sinh viên chưa đủ chuyên cần dự thi. Chỉ lưu điểm quá trình hoặc điều chỉnh chuyên cần hợp lệ.");
        updated(db.update("""
                UPDATE dang_ky SET diem_qua_trinh=?, diem_thi=? WHERE id=? AND lop_hoc_id IN (
                    SELECT l.id FROM lop_hoc l JOIN nguoi_dung u ON u.id=l.giang_vien_id WHERE u.ma_so=?
                )
                """, grades.coursework(), grades.exam(), id, actor.getName()));
    }

    @PutMapping("/session-management/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void editSession(@PathVariable long id, @RequestBody AttendanceController.SessionInput input, Principal actor) {
        schedule.lock();
        var session = ownedSession(id, actor);
        ensureGradingOpen(session.get("lop_hoc_id"));
        ensureUnrecorded(id, session);
        if (input.date() == null || input.date().isBefore(LocalDate.now(clock)) || input.start() == null || input.end() == null || !input.end().isAfter(input.start())) {
            throw bad("Chọn ngày học và giờ kết thúc sau giờ bắt đầu.");
        }
        if(!java.time.LocalDateTime.of(input.date(),input.end()).isAfter(java.time.LocalDateTime.now(clock)))
            throw bad("Không chuyển buổi học sang khung giờ đã kết thúc.");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm:ss");
        schedule.requireAvailable(((Number)session.get("lop_hoc_id")).longValue(),id,input.date().toString(),
                time.format(input.start()),time.format(input.end()),required(input.room(),20,"Phòng học"));
        updated(db.update("""
                UPDATE buoi_hoc SET ngay_hoc=?,gio_bat_dau=?,gio_ket_thuc=?,phong=?,noi_dung=? WHERE id=?
                """, input.date().toString(), time.format(input.start()), time.format(input.end()),
                required(input.room(), 20, "Phòng học"), input.topic() == null || input.topic().isBlank() ? null :
                required(input.topic(), 255, "Nội dung"), id));
        operations.notifyClass(((Number)session.get("lop_hoc_id")).longValue(),"Cập nhật lịch học","Lịch một buổi học đã thay đổi. Kiểm tra lại ngày, giờ và phòng trong lịch cá nhân.");
    }

    @DeleteMapping("/session-management/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void deleteSession(@PathVariable long id, Principal actor) {
        schedule.lock();
        var session=ownedSession(id, actor);
        ensureGradingOpen(session.get("lop_hoc_id"));
        ensureUnrecorded(id, session);
        updated(db.update("DELETE FROM buoi_hoc WHERE id=?", id));
        operations.notifyClass(((Number)session.get("lop_hoc_id")).longValue(),"Hủy buổi học","Một buổi học đã được hủy. Kiểm tra lịch học mới nhất.");
    }

    @PostMapping("/session-management/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void closeSession(@PathVariable long id, Principal actor) {
        ownedSession(id, actor);
        long now = clock.millis();
        if (db.update("UPDATE buoi_hoc SET han_diem_danh=?,ma_diem_danh=NULL,qr_secret=NULL WHERE id=? AND han_diem_danh>?",
                now, id, now) == 0) throw conflict("Buổi này không mở điểm danh.");
    }

    @GetMapping("/session-management/{id}/attendance")
    @Transactional(readOnly = true)
    List<Map<String, Object>> attendance(@PathVariable long id, Principal actor) {
        var session = ownedSession(id, actor);
        return db.queryForList("""
                SELECT s.id, s.ma_so AS code, s.ho_ten AS name, a.trang_thai AS status, a.ghi_chu AS note, a.thoi_gian AS "checkedAt",
                       x.id AS "requestId", x.ly_do AS reason, x.minh_chung AS evidence, x.trang_thai AS "requestStatus", l.da_chot_diem AS finalized
                FROM dang_ky d JOIN nguoi_dung s ON s.id=d.sinh_vien_id JOIN lop_hoc l ON l.id=d.lop_hoc_id
                LEFT JOIN diem_danh a ON a.sinh_vien_id=s.id AND a.buoi_hoc_id=?
                LEFT JOIN don_xin_phep x ON x.sinh_vien_id=s.id AND x.buoi_hoc_id=?
                WHERE d.lop_hoc_id=? AND (d.ngay_dang_ky IS NULL OR d.ngay_dang_ky<=?) ORDER BY s.ma_so
                """, id, id, session.get("lop_hoc_id"), session.get("ngay_hoc"));
    }

    @PutMapping("/session-management/{id}/attendance/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void editAttendance(@PathVariable long id, @PathVariable long studentId,
                        @RequestBody Mark mark, Principal actor) {
        var session = ownedSession(id, actor);
        ensureGradingOpen(session.get("lop_hoc_id"));
        if (LocalDate.parse(session.get("ngay_hoc").toString()).isAfter(LocalDate.now(clock))) {
            throw conflict("Chỉ ghi nhận từ ngày diễn ra buổi học.");
        }
        if (mark.status() == null || !Set.of("CO_MAT", "DI_MUON", "VANG", "CO_PHEP").contains(mark.status())) {
            throw bad("Hãy chọn trạng thái điểm danh.");
        }
        String note = mark.note() == null || mark.note().isBlank() ? null : required(mark.note(), 255, "Ghi chú");
        updated(db.update(dialect.choose("""
                INSERT INTO diem_danh(buoi_hoc_id,sinh_vien_id,trang_thai,ghi_chu)
                SELECT ?,d.sinh_vien_id,?,? FROM dang_ky d WHERE d.lop_hoc_id=? AND d.sinh_vien_id=?
                  AND (d.ngay_dang_ky IS NULL OR d.ngay_dang_ky<=?)
                ON CONFLICT(buoi_hoc_id,sinh_vien_id) DO UPDATE SET trang_thai=excluded.trang_thai,ghi_chu=excluded.ghi_chu
                ""","""
                INSERT INTO diem_danh(buoi_hoc_id,sinh_vien_id,trang_thai,ghi_chu)
                SELECT ?,d.sinh_vien_id,?,? FROM dang_ky d WHERE d.lop_hoc_id=? AND d.sinh_vien_id=?
                  AND (d.ngay_dang_ky IS NULL OR d.ngay_dang_ky<=?)
                ON DUPLICATE KEY UPDATE trang_thai=VALUES(trang_thai),ghi_chu=VALUES(ghi_chu)
                """), id, mark.status(), note, session.get("lop_hoc_id"), studentId, session.get("ngay_hoc")));
    }

    private void ensureGradingOpen(Object classId) {
        updated(db.update("UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=?", classId));
        if (db.queryForObject("SELECT da_chot_diem FROM lop_hoc WHERE id=?", Integer.class, classId) != 0) {
            throw conflict("Lớp đã chốt điểm và khóa điều chỉnh điểm danh.");
        }
    }

    private Map<String, Object> ownedSession(long id, Principal actor) {
        return db.queryForList("""
                SELECT b.lop_hoc_id,b.ngay_hoc,b.gio_ket_thuc,b.han_diem_danh FROM buoi_hoc b
                JOIN lop_hoc l ON l.id=b.lop_hoc_id JOIN nguoi_dung u ON u.id=l.giang_vien_id
                WHERE b.id=? AND u.ma_so=?
                """, id, actor.getName()).stream().findFirst().orElseThrow(AdminController::missing);
    }

    private void ensureUnrecorded(long id, Map<String, Object> session) {
        String end=session.get("ngay_hoc")+"T"+session.get("gio_ket_thuc");
        if (!java.time.LocalDateTime.parse(end).isAfter(java.time.LocalDateTime.now(clock)))
            throw conflict("Buổi học đã kết thúc; giữ lịch sử để tính chuyên cần.");
        if (session.get("han_diem_danh") != null
                || db.queryForObject("SELECT COUNT(*) FROM diem_danh WHERE buoi_hoc_id=?", Integer.class, id) > 0
                || db.queryForObject("SELECT COUNT(*) FROM don_xin_phep WHERE buoi_hoc_id=?", Integer.class, id) > 0) {
            throw conflict("Buổi học đã có lịch sử điểm danh hoặc đơn xin phép.");
        }
    }

    private static void validateGrade(BigDecimal score) {
        if (score != null && (score.signum() < 0 || score.compareTo(BigDecimal.TEN) > 0 || score.stripTrailingZeros().scale() > 2)) {
            throw bad("Điểm từ 0 đến 10 và tối đa hai chữ số thập phân.");
        }
    }

    record Grades(BigDecimal coursework, BigDecimal exam) {}
    record Mark(String status, String note) {}
}