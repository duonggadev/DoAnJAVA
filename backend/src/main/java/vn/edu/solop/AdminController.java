package vn.edu.solop;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
class AdminController {
    private final JdbcTemplate db;
    private final ScheduleRules schedule;
    private final DatabaseDialect dialect;
    private final BCryptPasswordEncoder passwords;
    private final java.time.Clock clock;
    private final OperationsStore operations;

    AdminController(JdbcTemplate db, BCryptPasswordEncoder passwords,java.time.Clock clock,DatabaseDialect dialect,
                    ScheduleRules schedule,OperationsStore operations) {
        this.schedule=schedule;
        this.dialect=dialect;
        this.clock=clock;
        this.db = db;
        this.passwords = passwords;
        this.operations = operations;
    }

    @GetMapping
    Map<String, Object> catalog() {
        return Map.of(
                "users", db.queryForList("""
                        SELECT n.id, n.ma_so AS code, n.ho_ten AS name, n.email, n.vai_tro AS role, n.bi_khoa AS locked,
                               (SELECT COUNT(*) FROM dang_ky d WHERE d.sinh_vien_id = n.id) AS "classCount",
                               h.lop_hanh_chinh_id AS "groupId", h.ngay_sinh AS "birthDate", h.gioi_tinh AS gender,
                               h.dien_thoai AS phone, h.trang_thai AS status, h.chuong_trinh_tot_nghiep_id AS "programId"
                        FROM nguoi_dung n LEFT JOIN ho_so_sinh_vien h ON h.sinh_vien_id = n.id ORDER BY n.ho_ten
                        """),
                "subjects", db.queryForList("""
                        SELECT id, ma_mon AS code, ten_mon AS name, so_tin_chi AS credits, mon_tien_quyet_id AS "prerequisiteId",
                         ty_le_chuyen_can AS attendance, ty_le_qua_trinh AS coursework, ty_le_thi AS exam FROM mon_hoc ORDER BY ma_mon
                        """),
                "terms", db.queryForList("""
                        SELECT ma AS code, mo_dang_ky AS "opensOn", dong_dang_ky AS "closesOn", nguong_chuyen_can AS threshold
                        FROM hoc_ky ORDER BY ma DESC
                        """),
                "classes", db.queryForList("""
                        SELECT l.id, l.ma_lop AS code, l.hoc_ky AS term, l.mon_hoc_id AS "subjectId", l.si_so_toi_da AS capacity,
                               l.giang_vien_id AS "teacherId", m.ten_mon AS subject, u.ho_ten AS teacher,
                               (SELECT COUNT(*) FROM dang_ky d WHERE d.lop_hoc_id = l.id) AS enrollment
                        FROM lop_hoc l JOIN mon_hoc m ON m.id = l.mon_hoc_id
                        JOIN nguoi_dung u ON u.id = l.giang_vien_id ORDER BY l.ma_lop
                        """),
                "groups", db.queryForList("SELECT g.id, g.ma AS code, g.ten AS name FROM lop_hanh_chinh g ORDER BY g.ma"),
                "programs", db.queryForList("SELECT p.id, p.ma AS code, p.ten AS name FROM chuong_trinh p ORDER BY p.ma"));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    void createUser(@RequestBody UserInput input) {
        saveUser(null, input, null);
    }

    /** CSV/Excel import: every row is validated first, so a bad row leaves the database untouched. */
    @PostMapping("/students/import")
    @Transactional
    Map<String, Object> importStudents(@RequestBody ImportInput input) {
        if (input == null || input.rows() == null || input.rows().isEmpty()) throw bad("Không có dòng sinh viên nào để nhập.");
        if (input.rows().size() > 500) throw bad("Mỗi lần nhập tối đa 500 sinh viên.");
        var errors = new ArrayList<Map<String, Object>>();
        var prepared = new ArrayList<UserInput>();
        var seen = new HashSet<String>();
        for (int index = 0; index < input.rows().size(); index++) {
            UserInput row = input.rows().get(index);
            try {
                if (row == null) throw bad("Dòng trống.");
                validateStudentProfile(row, true);
                if (row.password() == null || row.password().length() < 8) throw bad("Mật khẩu cần ít nhất 8 ký tự.");
                String normalized = code(row.code());
                if (!seen.add(normalized)) throw conflict("Mã tài khoản bị lặp trong tệp nhập.");
                if (db.queryForObject("SELECT COUNT(*) FROM nguoi_dung WHERE ma_so=?", Integer.class, normalized) > 0)
                    throw conflict("Mã tài khoản đã tồn tại.");
                prepared.add(new UserInput(row.code(), row.name(), row.email(), "SINH_VIEN", row.password(),
                        row.locked(), row.groupId(), row.birthDate(), row.gender(), row.phone(), row.status(), row.programId()));
            } catch (ResponseStatusException error) {
                errors.add(Map.of("row", index + 1, "code", row == null ? "" : String.valueOf(row.code()),
                        "message", error.getReason()));
            }
        }
        if (!errors.isEmpty()) return Map.of("created", 0, "errors", errors);
        for (UserInput row : prepared) saveUser(null, row, null);
        return Map.of("created", prepared.size(), "errors", List.of());
    }

    @PutMapping("/users/{id}")
    @Transactional
    void editUser(@PathVariable long id, @RequestBody UserInput input, Principal actor) {
        saveUser(id, input, actor);
    }

    private void saveUser(Long id, UserInput input, Principal actor) {
        String code = code(input.code());
        String name = required(input.name(), 100, "Họ tên");
        String email = input.email() == null || input.email().isBlank() ? null : input.email().strip().toLowerCase(Locale.ROOT);
        if (email != null && (email.length() > 100 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
            throw bad("Email chưa hợp lệ.");
        }
        String role = required(input.role(), 20, "Vai trò");
        if (!Set.of("ADMIN", "GIANG_VIEN", "SINH_VIEN").contains(role)) throw bad("Vai trò chưa hợp lệ.");
        String password = input.password();
        String hash = null;
        if (id == null || (password != null && !password.isEmpty())) {
            if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw bad("Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte.");
            }
            hash = passwords.encode(password);
        }
        var existing = id == null ? null : db.queryForList("SELECT ma_so, vai_tro, bi_khoa FROM nguoi_dung WHERE id = ?", id)
                .stream().findFirst().orElseThrow(AdminController::missing);
        Map<String, Object> profile = "SINH_VIEN".equals(role) ? studentProfile(input) : null;
        if (id == null) {
            db.update("INSERT INTO nguoi_dung (ma_so, ho_ten, email, vai_tro, mat_khau, bi_khoa) VALUES (?,?,?,?,?,?)",
                    code, name, email, role, hash, Boolean.TRUE.equals(input.locked()) ? 1 : 0);
            long created = db.queryForObject("SELECT id FROM nguoi_dung WHERE ma_so=?", Long.class, code);
            if (profile != null) saveStudentProfile(created, profile, false);
            if ("SINH_VIEN".equals(role)) operations.notifyUser(created, "Tài khoản sinh viên đã được tạo",
                    "Tài khoản " + code + " đã được Ban đào tạo khởi tạo. Liên hệ Ban đào tạo để nhận mật khẩu và hồ sơ lớp.");
            return;
        }
        int locked = input.locked() == null ? ((Number)existing.get("bi_khoa")).intValue() : input.locked() ? 1 : 0;
        if (actor.getName().equals(existing.get("ma_so")) && (!role.equals("ADMIN") || !code.equals(actor.getName()) || locked != 0)) {
            throw conflict("Bạn chỉ có thể đổi tên, email hoặc mật khẩu của tài khoản đang dùng.");
        }
        if (!role.equals(existing.get("vai_tro"))) {
            if(db.queryForObject("SELECT COUNT(*) FROM ho_so_sinh_vien WHERE sinh_vien_id=?",Integer.class,id)>0)
                throw conflict("Tài khoản đã có hồ sơ sinh viên nên không thể đổi vai trò.");
            int assigned = db.queryForObject("""
                    SELECT (SELECT COUNT(*) FROM lop_hoc WHERE giang_vien_id = ?)
                         + (SELECT COUNT(*) FROM dang_ky WHERE sinh_vien_id = ?)
                         + (SELECT COUNT(*) FROM diem_danh WHERE sinh_vien_id = ?)
                    """, Integer.class, id, id, id);
            if (assigned > 0) throw conflict("Tài khoản có dữ liệu học tập nên chưa thể đổi vai trò.");
        }
        boolean revoke = hash != null || !role.equals(existing.get("vai_tro")) || !code.equals(existing.get("ma_so"))
                || locked != ((Number)existing.get("bi_khoa")).intValue();
        updated(db.update("""
                UPDATE nguoi_dung SET ma_so=?, ho_ten=?, email=?, vai_tro=?, mat_khau=COALESCE(?,mat_khau),
                    bi_khoa=?, ban_phien=ban_phien+? WHERE id=?
                """, code, name, email, role, hash, locked, revoke ? 1 : 0, id));
        if (profile != null) saveStudentProfile(id, profile,
                db.queryForObject("SELECT COUNT(*) FROM ho_so_sinh_vien WHERE sinh_vien_id=?", Integer.class, id) > 0);
    }

    /** Used by bulk import: a student row must carry at least the class group. */
    private void validateStudentProfile(UserInput input, boolean required) {
        Map<String, Object> profile = studentProfile(input);
        if (required && profile == null) throw bad("Thiếu thông tin hồ sơ sinh viên (lớp hành chính).");
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }

    /** Returns null when no profile field is supplied, so plain account edits stay untouched. */
    private Map<String, Object> studentProfile(UserInput input) {
        boolean supplied = input.groupId() != null || present(input.birthDate()) || present(input.gender())
                || present(input.phone()) || present(input.status()) || input.programId() != null;
        if (!supplied) return null;
        if (input.groupId() == null || db.queryForObject("SELECT COUNT(*) FROM lop_hanh_chinh WHERE id=?",
                Integer.class, input.groupId()) == 0) throw bad("Hãy chọn lớp hành chính của sinh viên.");
        String status = input.status() == null || input.status().isBlank() ? "DANG_HOC" : input.status();
        if (!Set.of("DANG_HOC", "BAO_LUU", "THOI_HOC", "TOT_NGHIEP").contains(status)) throw bad("Trạng thái sinh viên chưa hợp lệ.");
        String gender = input.gender() == null || input.gender().isBlank() ? null : input.gender();
        if (gender != null && !Set.of("NAM", "NU", "KHAC").contains(gender)) throw bad("Giới tính chưa hợp lệ.");
        String birth = null;
        if (input.birthDate() != null && !input.birthDate().isBlank()) {
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(input.birthDate());
                if (date.getYear() < 1900 || date.isAfter(java.time.LocalDate.now(clock))) throw bad("Ngày sinh chưa hợp lệ.");
                birth = date.toString();
            } catch (java.time.format.DateTimeParseException error) {
                throw bad("Ngày sinh phải có dạng YYYY-MM-DD.");
            }
        }
        String phone = input.phone() == null || input.phone().isBlank() ? null : input.phone().strip();
        if (phone != null && phone.length() > 25) throw bad("Số điện thoại tối đa 25 ký tự.");
        if (input.programId() != null && db.queryForObject("SELECT COUNT(*) FROM chuong_trinh WHERE id=?",
                Integer.class, input.programId()) == 0) throw bad("Chương trình tốt nghiệp chưa hợp lệ.");
        var profile = new LinkedHashMap<String, Object>();
        profile.put("groupId", input.groupId());
        profile.put("birthDate", birth);
        profile.put("gender", gender);
        profile.put("phone", phone);
        profile.put("status", status);
        profile.put("programId", input.programId());
        return profile;
    }

    private void saveStudentProfile(long student, Map<String, Object> profile, boolean exists) {
        Object[] values = {profile.get("groupId"), profile.get("birthDate"), profile.get("gender"),
                profile.get("phone"), profile.get("status"), profile.get("programId")};
        if (exists) {
            db.update("UPDATE ho_so_sinh_vien SET lop_hanh_chinh_id=?,ngay_sinh=?,gioi_tinh=?,dien_thoai=?,trang_thai=?,"
                    + "chuong_trinh_tot_nghiep_id=? WHERE sinh_vien_id=?",
                    java.util.stream.Stream.concat(java.util.Arrays.stream(values), java.util.stream.Stream.of(student)).toArray());
        } else {
            db.update("INSERT INTO ho_so_sinh_vien(sinh_vien_id,lop_hanh_chinh_id,ngay_sinh,gioi_tinh,dien_thoai,trang_thai,"
                    + "chuong_trinh_tot_nghiep_id) VALUES (?,?,?,?,?,?,?)",
                    java.util.stream.Stream.concat(java.util.stream.Stream.of(student), java.util.Arrays.stream(values)).toArray());
        }
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    void createSubject(@RequestBody SubjectInput input) {
        saveSubject(null, input);
    }

    @PutMapping("/subjects/{id}")
    @Transactional
    void editSubject(@PathVariable long id, @RequestBody SubjectInput input) {
        saveSubject(id, input);
    }

    private void saveSubject(Long id, SubjectInput input) {
        validateCredits(input.credits());
        String code = code(input.code());
        String name = required(input.name(), 100, "Tên môn");
        int attendance = input.attendance() == null ? 10 : input.attendance();
        int coursework = input.coursework() == null ? 30 : input.coursework();
        int exam = input.exam() == null ? 60 : input.exam();
        if (attendance < 0 || attendance > 100 || coursework < 0 || coursework > 100 || exam < 0 || exam > 100
                || attendance + coursework + exam != 100) throw bad("Các trọng số phải từ 0 đến 100 và có tổng bằng 100.");
        if(id!=null) {
            updated(db.update("UPDATE mon_hoc SET ma_mon=ma_mon WHERE id=?",id));
            int frozen=db.queryForObject("""
                    SELECT COUNT(*) FROM mon_hoc m WHERE m.id=? AND
                    (m.so_tin_chi<>? OR m.ty_le_chuyen_can<>? OR m.ty_le_qua_trinh<>? OR m.ty_le_thi<>?)
                    AND EXISTS(SELECT 1 FROM lop_hoc l WHERE l.mon_hoc_id=m.id AND l.da_chot_diem=1)
                    """,Integer.class,id,input.credits(),attendance,coursework,exam);
            if(frozen>0)throw conflict("Môn đã có bảng điểm chốt. Cần mở lại các bảng điểm liên quan trước khi đổi tín chỉ hoặc trọng số.");
        }
        Long prerequisite = input.prerequisiteId();
        if (prerequisite != null && id != null && db.queryForObject("""
                WITH RECURSIVE chain(id) AS (
                    SELECT id FROM mon_hoc WHERE id=?
                    UNION SELECT m.mon_tien_quyet_id FROM mon_hoc m JOIN chain c ON m.id=c.id WHERE m.mon_tien_quyet_id IS NOT NULL
                ) SELECT COUNT(*) FROM chain WHERE id=?
                """, Integer.class, prerequisite, id) > 0) throw conflict("Môn tiên quyết tạo thành vòng lặp.");
        if (id == null) {
            db.update("""
                    INSERT INTO mon_hoc (ma_mon,ten_mon,so_tin_chi,mon_tien_quyet_id,ty_le_chuyen_can,ty_le_qua_trinh,ty_le_thi)
                    VALUES (?,?,?,?,?,?,?)
                    """, code, name, input.credits(), prerequisite, attendance, coursework, exam);
        } else {
            updated(db.update("""
                    UPDATE mon_hoc SET ma_mon=?,ten_mon=?,so_tin_chi=?,mon_tien_quyet_id=?,ty_le_chuyen_can=?,ty_le_qua_trinh=?,ty_le_thi=?
                    WHERE id=?
                    """, code, name, input.credits(), prerequisite, attendance, coursework, exam, id));
        }
    }

    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    void createClass(@RequestBody ClassInput input) {
        validateClass(input);
        db.update("INSERT INTO lop_hoc (ma_lop, mon_hoc_id, giang_vien_id, hoc_ky, si_so_toi_da) VALUES (?,?,?,?,?)",
                code(input.code()), input.subjectId(), input.teacherId(), input.term(), capacity(input));
    }

    @PutMapping("/classes/{id}")
    @Transactional
    void editClass(@PathVariable long id, @RequestBody ClassInput input) {
        schedule.lock();
        validateClass(input);
        updated(db.update("UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=?", id));
        int teachingConflict=db.queryForObject("""
                SELECT COUNT(*) FROM buoi_hoc target JOIN buoi_hoc other
                  ON target.ngay_hoc=other.ngay_hoc AND target.lop_hoc_id<>other.lop_hoc_id
                  AND substr(concat(target.gio_bat_dau,':00'),1,8)<substr(concat(other.gio_ket_thuc,':00'),1,8)
                  AND substr(concat(other.gio_bat_dau,':00'),1,8)<substr(concat(target.gio_ket_thuc,':00'),1,8)
                JOIN lop_hoc owner ON owner.id=other.lop_hoc_id
                WHERE target.lop_hoc_id=? AND owner.giang_vien_id=? AND target.ngay_hoc>=?
                """,Integer.class,id,input.teacherId(),java.time.LocalDate.now(clock).toString());
        if(teachingConflict>0)throw conflict("Giảng viên được chọn bị trùng lịch dạy với lớp khác.");
        int count = db.queryForObject("SELECT COUNT(*) FROM dang_ky WHERE lop_hoc_id=?", Integer.class, id);
        if (count > capacity(input)) throw conflict("Sĩ số tối đa thấp hơn số sinh viên đã đăng ký.");
        int hasHistory = db.queryForObject("""
                SELECT COUNT(*) FROM lop_hoc WHERE id=? AND (mon_hoc_id<>? OR hoc_ky<>?) AND
                  (EXISTS(SELECT 1 FROM dang_ky WHERE lop_hoc_id=?) OR EXISTS(SELECT 1 FROM buoi_hoc WHERE lop_hoc_id=?))
                """, Integer.class, id, input.subjectId(), input.term(), id, id);
        if (hasHistory > 0) throw conflict("Lớp đã có đăng ký hoặc buổi học nên không thể đổi môn hoặc học kỳ.");
        updated(db.update("UPDATE lop_hoc SET ma_lop=?,mon_hoc_id=?,giang_vien_id=?,hoc_ky=?,si_so_toi_da=? WHERE id=?",
                code(input.code()), input.subjectId(), input.teacherId(), input.term(), capacity(input), id));
    }

    @GetMapping("/classes/{id}/roster")
    List<Map<String, Object>> roster(@PathVariable long id) {
        requireClass(id);
        return db.queryForList("""
                SELECT u.id, u.ma_so AS code, u.ho_ten AS name, u.bi_khoa AS locked,
                       d.ngay_dang_ky AS enrolledOn,
                       (SELECT COUNT(*) FROM diem_danh a JOIN buoi_hoc b ON b.id = a.buoi_hoc_id
                        WHERE b.lop_hoc_id = d.lop_hoc_id AND a.sinh_vien_id = d.sinh_vien_id) AS attended
                FROM dang_ky d JOIN nguoi_dung u ON u.id = d.sinh_vien_id
                WHERE d.lop_hoc_id = ? ORDER BY u.ma_so
                """, id);
    }

    @PostMapping("/classes/{id}/roster")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    void enrollStudent(@PathVariable long id, @RequestBody RosterInput input) {
        schedule.lock();
        requireClass(id);
        updated(db.update("UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=?", id));
        if (input.studentId() == null || db.queryForObject(
                "SELECT COUNT(*) FROM nguoi_dung WHERE id=? AND vai_tro='SINH_VIEN' AND bi_khoa=0",
                Integer.class, input.studentId()) == 0) throw bad("Chọn một tài khoản sinh viên đang hoạt động.");
        if (db.queryForObject("SELECT da_chot_diem FROM lop_hoc WHERE id=?", Integer.class, id) != 0)
            throw conflict("Lớp đã chốt điểm.");
        if (db.queryForObject("SELECT COUNT(*) FROM dang_ky WHERE lop_hoc_id=? AND sinh_vien_id=?",
                Integer.class, id, input.studentId()) > 0) throw conflict("Sinh viên đã có trong lớp này.");
        var limits = db.queryForMap("""
                SELECT l.si_so_toi_da AS capacity,
                       (SELECT COUNT(*) FROM dang_ky WHERE lop_hoc_id=l.id) AS enrollment,
                       l.mon_hoc_id, l.hoc_ky FROM lop_hoc l WHERE l.id=?
                """, id);
        if (((Number)limits.get("enrollment")).intValue() >= ((Number)limits.get("capacity")).intValue())
            throw conflict("Lớp đã đủ sĩ số.");
        if (db.queryForObject("""
                SELECT COUNT(*) FROM dang_ky d JOIN lop_hoc other ON other.id = d.lop_hoc_id
                WHERE d.sinh_vien_id=? AND other.mon_hoc_id=? AND other.hoc_ky=?
                """, Integer.class, input.studentId(), limits.get("mon_hoc_id"), limits.get("hoc_ky")) > 0)
            throw conflict("Sinh viên đã có lớp khác của cùng môn trong học kỳ này.");
        if (db.queryForObject("""
                SELECT COUNT(*) FROM dang_ky d JOIN buoi_hoc x ON x.lop_hoc_id = d.lop_hoc_id
                JOIN buoi_hoc y ON y.lop_hoc_id = ? AND y.ngay_hoc = x.ngay_hoc
                  AND substr(concat(y.gio_bat_dau,':00'),1,8) < substr(concat(x.gio_ket_thuc,':00'),1,8)
                  AND substr(concat(x.gio_bat_dau,':00'),1,8) < substr(concat(y.gio_ket_thuc,':00'),1,8)
                WHERE d.sinh_vien_id = ? AND d.lop_hoc_id <> ? AND y.ngay_hoc >= ?
                """, Integer.class, id, input.studentId(), id, java.time.LocalDate.now(clock).toString()) > 0)
            throw conflict("Sinh viên bị trùng lịch học với lớp này.");
        updated(db.update("INSERT INTO dang_ky(lop_hoc_id,sinh_vien_id,ngay_dang_ky) VALUES (?,?,?)",
                id, input.studentId(), java.time.LocalDate.now(clock).toString()));
        operations.notifyUser(input.studentId(), "Phân lớp học phần",
                "Ban đào tạo đã xếp bạn vào lớp " + db.queryForObject("SELECT ma_lop FROM lop_hoc WHERE id=?", String.class, id)
                        + ". Xem lịch học để theo dõi buổi học.");
    }

    @DeleteMapping("/classes/{id}/roster/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void unenrollStudent(@PathVariable long id, @PathVariable long studentId) {
        schedule.lock();
        requireClass(id);
        updated(db.update("UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=?", id));
        if (db.queryForObject("SELECT da_chot_diem FROM lop_hoc WHERE id=?", Integer.class, id) != 0)
            throw conflict("Lớp đã chốt điểm.");
        var record = db.queryForList("""
                SELECT d.id, d.diem_qua_trinh, d.diem_thi,
                  CASE WHEN EXISTS(SELECT 1 FROM diem_danh a JOIN buoi_hoc b ON b.id=a.buoi_hoc_id
                      WHERE b.lop_hoc_id=d.lop_hoc_id AND a.sinh_vien_id=d.sinh_vien_id)
                    OR EXISTS(SELECT 1 FROM don_xin_phep x JOIN buoi_hoc b ON b.id=x.buoi_hoc_id
                      WHERE b.lop_hoc_id=d.lop_hoc_id AND x.sinh_vien_id=d.sinh_vien_id) THEN 1 ELSE 0 END AS "hasHistory",
                  CASE WHEN EXISTS(SELECT 1 FROM buoi_hoc b WHERE b.lop_hoc_id=d.lop_hoc_id
                      AND (d.ngay_dang_ky IS NULL OR b.ngay_hoc>=d.ngay_dang_ky)
                      AND (b.han_diem_danh<=? OR (b.han_diem_danh IS NULL AND
                        concat(b.ngay_hoc,'T',substr(concat(b.gio_ket_thuc,':00'),1,8))<=?))) THEN 1 ELSE 0 END AS "completed"
                FROM dang_ky d WHERE d.lop_hoc_id=? AND d.sinh_vien_id=?
                """, clock.millis(), AcademicController.cutoff(clock), id, studentId)
                .stream().findFirst().orElseThrow(AdminController::missing);
        if (record.get("diem_qua_trinh") != null || record.get("diem_thi") != null
                || ((Number)record.get("hasHistory")).intValue() != 0 || ((Number)record.get("completed")).intValue() != 0)
            throw conflict("Không thể gỡ sinh viên khi lớp đã có điểm, kết quả điểm danh hoặc buổi học đã kết thúc.");
        updated(db.update("DELETE FROM dang_ky WHERE id=?", ((Number)record.get("id")).longValue()));
        operations.notifyUser(studentId, "Điều chỉnh lớp học phần",
                "Ban đào tạo đã gỡ bạn khỏi lớp " + db.queryForObject("SELECT ma_lop FROM lop_hoc WHERE id=?", String.class, id)
                        + ". Liên hệ Ban đào tạo nếu cần hỗ trợ.");
    }

    private void requireClass(long id) {
        if (db.queryForObject("SELECT COUNT(*) FROM lop_hoc WHERE id=?", Integer.class, id) == 0) throw missing();
    }

    @PutMapping("/terms/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void saveTerm(@PathVariable String code, @RequestBody TermInput input) {
        if (!code.matches("[0-9]{4}-[1-3]") || input.opensOn() == null || input.closesOn() == null
                || input.closesOn().isBefore(input.opensOn()) || input.threshold() < 0 || input.threshold() > 100) {
            throw bad("Kiểm tra mã học kỳ, ngày đăng ký và ngưỡng chuyên cần (0–100%).");
        }
        db.update(dialect.choose("""
                INSERT INTO hoc_ky (ma,mo_dang_ky,dong_dang_ky,nguong_chuyen_can) VALUES (?,?,?,?)
                ON CONFLICT(ma) DO UPDATE SET mo_dang_ky=excluded.mo_dang_ky,dong_dang_ky=excluded.dong_dang_ky,
                    nguong_chuyen_can=excluded.nguong_chuyen_can
                ""","""
                INSERT INTO hoc_ky (ma,mo_dang_ky,dong_dang_ky,nguong_chuyen_can) VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE mo_dang_ky=VALUES(mo_dang_ky),dong_dang_ky=VALUES(dong_dang_ky),
                    nguong_chuyen_can=VALUES(nguong_chuyen_can)
                """), code, input.opensOn().toString(), input.closesOn().toString(), input.threshold());
    }

    private static int capacity(ClassInput input) {
        int limit = input.capacity() == null ? 60 : input.capacity();
        if (limit < 1 || limit > 500) throw bad("Sĩ số tối đa phải từ 1 đến 500.");
        return limit;
    }

    @DeleteMapping("/{entity:users|subjects|classes}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@PathVariable String entity, @PathVariable long id, Principal actor) {
        String statement = switch (entity) {
            case "users" -> {
                var names = db.queryForList("SELECT ma_so FROM nguoi_dung WHERE id = ?", String.class, id);
                if (names.contains(actor.getName())) throw conflict("Không thể xóa tài khoản đang đăng nhập.");
                yield "DELETE FROM nguoi_dung WHERE id = ?";
            }
            case "subjects" -> "DELETE FROM mon_hoc WHERE id = ?";
            case "classes" -> "DELETE FROM lop_hoc WHERE id = ?";
            default -> throw missing();
        };
        updated(db.update(statement, id));
    }

    private void validateClass(ClassInput input) {
        if (input.term() == null || !input.term().matches("[0-9]{4}-[1-3]")) throw bad("Học kỳ phải có dạng 2026-1.");
        if (db.queryForObject("SELECT COUNT(*) FROM nguoi_dung WHERE id = ? AND vai_tro = 'GIANG_VIEN' AND bi_khoa=0",
                Integer.class, input.teacherId()) == 0) throw bad("Hãy chọn giảng viên phụ trách.");
        if (db.queryForObject("SELECT COUNT(*) FROM mon_hoc WHERE id = ?", Integer.class, input.subjectId()) == 0) {
            throw bad("Hãy chọn môn học.");
        }
    }

    private static void validateCredits(int credits) {
        if (credits < 1 || credits > 30) throw bad("Số tín chỉ phải từ 1 đến 30.");
    }

    private static String code(String raw) {
        String value = required(raw, 20, "Mã");
        if (!value.matches("[A-Za-z0-9._-]+")) throw bad("Mã chỉ dùng chữ không dấu, số, dấu chấm, gạch dưới và gạch ngang.");
        return value;
    }

    static String required(String value, int max, String label) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw bad(label + " cần từ 1 đến " + max + " ký tự.");
        }
        return value.strip();
    }

    static void updated(int rows) {
        if (rows == 0) throw missing();
    }

    static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu.");
    }

    static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    record UserInput(String code, String name, String email, String role, String password, Boolean locked,
                     Long groupId, String birthDate, String gender, String phone, String status, Long programId) {}
    record ImportInput(java.util.List<UserInput> rows) {}
    record SubjectInput(String code, String name, int credits, Long prerequisiteId, Integer attendance,
                        Integer coursework, Integer exam) {}
    record ClassInput(String code, int subjectId, int teacherId, String term, Integer capacity) {}
    record TermInput(java.time.LocalDate opensOn, java.time.LocalDate closesOn, int threshold) {}
    record RosterInput(Long studentId) {}
}