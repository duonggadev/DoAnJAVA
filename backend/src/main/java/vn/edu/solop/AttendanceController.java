package vn.edu.solop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Base64;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class AttendanceController {
    private final JdbcTemplate db;
    private final StudentEligibility eligibility;
    private final OperationsStore operations;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final AttemptLimits limits;
    private final BrowserDevice devices;
    private final ScheduleRules schedule;
    private final QrTickets tickets;

    AttendanceController(JdbcTemplate db, Clock clock, AttemptLimits limits, BrowserDevice devices, ScheduleRules schedule, QrTickets tickets,StudentEligibility eligibility,OperationsStore operations) {
        this.eligibility=eligibility;
        this.operations=operations;
        this.db = db;
        this.clock = clock;
        this.limits = limits;
        this.devices = devices;
        this.schedule=schedule;
        this.tickets=tickets;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        devices.identify(request, response);
        return Map.of("token", token.getToken(), "header", token.getHeaderName());
    }

    @GetMapping("/me")
    Account me(Principal principal) {
        return db.queryForObject("""
                SELECT id, ma_so, ho_ten, vai_tro FROM nguoi_dung WHERE ma_so = ?
                """, (row, index) -> new Account(row.getLong(1), row.getString(2), row.getString(3), row.getString(4)),
                principal.getName());
    }

    @GetMapping("/classes")
    List<Map<String, Object>> classes(Principal principal) {
        Account user = me(principal);
        return db.queryForList("""
                SELECT l.id, l.ma_lop AS code, m.ten_mon AS subject, m.so_tin_chi AS credits,
                       l.hoc_ky AS term, u.ho_ten AS teacher,
                       (SELECT count(*) FROM dang_ky WHERE lop_hoc_id = l.id) AS enrollment
                FROM lop_hoc l JOIN mon_hoc m ON l.mon_hoc_id = m.id
                JOIN nguoi_dung u ON l.giang_vien_id = u.id
                WHERE ? = 'ADMIN' OR l.giang_vien_id = ? OR EXISTS (
                    SELECT 1 FROM dang_ky WHERE lop_hoc_id = l.id AND sinh_vien_id = ?
                )
                ORDER BY l.ma_lop
                """, user.role(), user.id(), user.id());
    }

    @GetMapping("/classes/{id}")
    Map<String, Object> classBook(@PathVariable long id, Principal principal) {
        Account user = me(principal);
        requireClass(id, user);
        boolean student = user.role().equals("SINH_VIEN");
        var sessions = db.queryForList("""
                SELECT b.id, b.ngay_hoc AS date, b.gio_bat_dau AS start, b.gio_ket_thuc AS end,
                       b.phong AS room, b.noi_dung AS topic, b.han_diem_danh AS "expiresAt",
                       CASE WHEN ? = 'GIANG_VIEN' AND b.han_diem_danh > ? THEN b.ma_diem_danh END AS pin,
                       CASE WHEN b.han_diem_danh IS NULL AND (concat(b.ngay_hoc,'T',substr(concat(b.gio_ket_thuc,':00'),1,8)))>?
                         AND NOT EXISTS (SELECT 1 FROM lop_hoc WHERE id=b.lop_hoc_id AND da_chot_diem=1)
                         AND NOT EXISTS (SELECT 1 FROM don_xin_phep WHERE buoi_hoc_id=b.id)
                         AND NOT EXISTS (
                           SELECT 1 FROM diem_danh WHERE buoi_hoc_id=b.id
                       ) THEN 1 ELSE 0 END AS editable,
                       (SELECT trang_thai FROM diem_danh WHERE buoi_hoc_id = b.id AND sinh_vien_id = ?) AS status
                FROM buoi_hoc b WHERE b.lop_hoc_id = ? ORDER BY b.ngay_hoc, b.gio_bat_dau, b.id
                """, user.role(), clock.millis(), AcademicController.cutoff(clock), user.id(), id);
        var roster = db.queryForList("""
                SELECT u.ma_so AS code, u.ho_ten AS name,
                       COUNT(CASE WHEN b.id IS NOT NULL AND (a.trang_thai IS NULL OR a.trang_thai<>'CO_PHEP') THEN 1 END) AS total,
                       COUNT(CASE WHEN a.trang_thai IN ('CO_MAT','DI_MUON') THEN 1 END) AS attended,
                       COUNT(CASE WHEN b.id IS NOT NULL AND (a.trang_thai IS NULL OR a.trang_thai = 'VANG') THEN 1 END) AS absent,
                       COUNT(CASE WHEN a.trang_thai='CO_PHEP' THEN 1 END) AS excused
                FROM dang_ky d JOIN nguoi_dung u ON u.id = d.sinh_vien_id
                LEFT JOIN buoi_hoc b ON b.lop_hoc_id = d.lop_hoc_id
                    AND (d.ngay_dang_ky IS NULL OR b.ngay_hoc >= d.ngay_dang_ky)
                    AND (b.han_diem_danh <= ? OR (b.han_diem_danh IS NULL AND (concat(b.ngay_hoc,'T',substr(concat(b.gio_ket_thuc,':00'),1,8))) <= ?))
                LEFT JOIN diem_danh a ON a.buoi_hoc_id = b.id AND a.sinh_vien_id = d.sinh_vien_id
                WHERE d.lop_hoc_id = ? AND (? = 0 OR u.id = ?)
                GROUP BY u.id, u.ma_so, u.ho_ten ORDER BY u.ma_so
                """, clock.millis(), AcademicController.cutoff(clock), id, student ? 1 : 0, user.id());
        return Map.of("sessions", sessions, "roster", roster,
                "finalized", db.queryForObject("SELECT da_chot_diem FROM lop_hoc WHERE id=?", Integer.class,id));
    }

    @PostMapping("/classes/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    void createSession(@PathVariable long id, @RequestBody SessionInput input, Principal principal) {
        schedule.lock();
        Account actor = me(principal);
        if (db.update("UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=? AND giang_vien_id=?", id, actor.id()) == 0) {
            throw AdminController.missing();
        }
        if (input.date() == null || input.date().isBefore(LocalDate.now(clock))
                || input.start() == null || input.end() == null || !input.end().isAfter(input.start())
                || input.room() == null || input.room().isBlank() || input.room().strip().length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kiểm tra ngày, giờ và phòng học.");
        }
        if(!java.time.LocalDateTime.of(input.date(),input.end()).isAfter(java.time.LocalDateTime.now(clock)))
            throw AdminController.bad("Không tạo buổi học trong khung giờ đã kết thúc.");
        if (db.queryForObject("SELECT da_chot_diem FROM lop_hoc WHERE id=?", Integer.class,id)!=0) {
            throw AdminController.conflict("Lớp đã chốt điểm.");
        }
        int weeks = input.weeks() == null ? 1 : input.weeks();
        if (weeks < 1 || weeks > 20) throw AdminController.bad("Số tuần phải từ 1 đến 20.");
        String topic = input.topic() == null || input.topic().isBlank() ? null : AdminController.required(input.topic(), 255, "Nội dung");
        var timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");
        String start = timeFormat.format(input.start());
        String end = timeFormat.format(input.end());
        for (int week = 0; week < weeks; week++) {
            String date = input.date().plusWeeks(week).toString();
            schedule.requireAvailable(id,-1,date,start,end,input.room().strip());
            db.update("""
                    INSERT INTO buoi_hoc (lop_hoc_id, ngay_hoc, gio_bat_dau, gio_ket_thuc, phong, noi_dung)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, id, date, start, end, input.room().strip(), topic);
        }
        operations.notifyClass(id,"Lịch học mới","Giảng viên đã thêm buổi học. Xem lịch cá nhân để theo dõi ngày, giờ và phòng.");
    }

    @PostMapping("/sessions/{id}/open")
    @Transactional
    void open(@PathVariable long id, Principal principal) {
        Account user = me(principal);
        if(db.update("""
                UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=(SELECT lop_hoc_id FROM buoi_hoc WHERE id=?)
                  AND giang_vien_id=? AND da_chot_diem=0
                """,id,user.id())==0) throw AdminController.conflict("Bạn không phụ trách buổi học hoặc lớp đã chốt điểm.");
        long now = clock.millis();
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        int changed = db.update("""
                UPDATE buoi_hoc SET ma_diem_danh = ?, han_diem_danh = ?, qr_secret = ?
                WHERE id = ? AND ngay_hoc = ? AND (han_diem_danh IS NULL OR han_diem_danh<=?)
                  AND lop_hoc_id IN (SELECT id FROM lop_hoc WHERE giang_vien_id = ? AND da_chot_diem=0)
                """, String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000)), now + 600_000,
                Base64.getUrlEncoder().withoutPadding().encodeToString(secret), id, LocalDate.now(clock).toString(), now, user.id());
        if (changed == 0 && db.queryForObject("""
                SELECT COUNT(*) FROM buoi_hoc WHERE id=? AND ngay_hoc=? AND han_diem_danh>?
                """,Integer.class,id,LocalDate.now(clock).toString(),now)==0) {
            throw AdminController.conflict("Chỉ mở điểm danh cho buổi học trong ngày hôm nay.");
        }
    }

    @GetMapping("/sessions/{id}/qr")
    ResponseEntity<Map<String, Object>> qr(@PathVariable long id, Principal principal) {
        long now = clock.millis();
        var rows = db.queryForList("""
                SELECT b.qr_secret FROM buoi_hoc b JOIN lop_hoc l ON l.id=b.lop_hoc_id
                WHERE b.id=? AND b.han_diem_danh>? AND l.giang_vien_id=?
                """, id, now, me(principal).id());
        if (rows.isEmpty()) throw AdminController.missing();
        String secret = (String)rows.getFirst().get("qr_secret");
        if (secret == null) throw AdminController.conflict("Buổi cũ chỉ hỗ trợ điểm danh bằng PIN.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "token", RotatingQr.current(secret, id, now), "refreshAt", RotatingQr.nextRefresh(now), "serverTime", now));
    }

    @PostMapping("/qr-tickets")
    Map<String,String> qrTicket(@RequestBody TicketInput input,HttpServletRequest request) {
        if(input.token()==null || !input.token().matches("[A-Za-z0-9_-]{43}"))throw AdminController.bad("Mã QR chưa hợp lệ.");
        String key="qr-link:"+request.getRemoteAddr();
        if(!limits.reserve(key,60,60_000))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Bạn đã thử nhiều mã QR sai. Hãy chờ một phút.");
        long now=clock.millis();
        var rows=db.queryForList("""
                SELECT b.qr_secret FROM buoi_hoc b JOIN lop_hoc l ON l.id=b.lop_hoc_id
                WHERE b.id=? AND b.han_diem_danh>? AND l.da_chot_diem=0
                """,input.session(),now);
        if(rows.isEmpty() || !RotatingQr.matches((String)rows.getFirst().get("qr_secret"),input.session(),now,input.token())) {
            throw AdminController.conflict("QR đã đổi hoặc buổi học đã đóng. Quét QR mới hoặc dùng PIN.");
        }
        limits.refund(key);
        return Map.of("ticket",tickets.issue(request,input.session(),(String)rows.getFirst().get("qr_secret")));
    }

    @GetMapping("/sessions/{id}/check-in-context")
    Map<String,Object> checkInContext(@PathVariable long id, Principal principal) {
        var rows=db.queryForList("""
                SELECT b.id, l.id AS "classId", l.ma_lop AS code, m.ten_mon AS subject,
                    b.ngay_hoc AS date, b.gio_bat_dau AS start, b.gio_ket_thuc AS end, b.phong AS room,
                    CASE WHEN b.han_diem_danh>? AND l.da_chot_diem=0 THEN 1 ELSE 0 END AS open,
                    a.trang_thai AS status
                FROM buoi_hoc b JOIN lop_hoc l ON l.id=b.lop_hoc_id JOIN mon_hoc m ON m.id=l.mon_hoc_id
                JOIN dang_ky d ON d.lop_hoc_id=l.id JOIN nguoi_dung s ON s.id=d.sinh_vien_id
                LEFT JOIN diem_danh a ON a.buoi_hoc_id=b.id AND a.sinh_vien_id=s.id
                WHERE b.id=? AND s.ma_so=? AND s.vai_tro='SINH_VIEN'
                    AND (d.ngay_dang_ky IS NULL OR b.ngay_hoc>=d.ngay_dang_ky)
                """,clock.millis(),id,principal.getName());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Không tìm thấy buổi học trong các lớp bạn đã đăng ký. Kiểm tra tài khoản sinh viên và lớp học.");
        return rows.getFirst();
    }

    @PostMapping("/sessions/{id}/check-in")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void checkIn(@PathVariable long id, @RequestBody CheckIn input, Principal principal,
                 HttpServletRequest request, HttpServletResponse response) {
        Account user = me(principal);
        eligibility.requireActive(principal.getName());
        int methods=(input.pin()!=null?1:0)+(input.token()!=null?1:0)+(input.ticket()!=null?1:0);
        if (methods!=1
                || (input.pin() != null && !input.pin().matches("[0-9]{8}"))
                || (input.token() != null && !input.token().matches("[A-Za-z0-9_-]{43}"))
                || (input.ticket()!=null && !input.ticket().matches("[a-f0-9-]{36}"))) {
            throw AdminController.bad("Nhập PIN tám chữ số hoặc quét mã QR của buổi học.");
        }
        String attempt = "pin:" + user.id();
        if (!limits.reserve(attempt, 5, 60_000)) {
            response.setHeader("Retry-After", "60");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã thử quá nhiều lần. Hãy đợi một phút.");
        }
        db.update("UPDATE buoi_hoc SET ngay_hoc=ngay_hoc WHERE id=?", id);
        long now = clock.millis();
        var sessions = db.queryForList("""
                SELECT b.qr_secret, b.ma_diem_danh FROM buoi_hoc b JOIN dang_ky d ON d.lop_hoc_id=b.lop_hoc_id
                WHERE b.id=? AND b.han_diem_danh>? AND d.sinh_vien_id=?
                """, id, now, user.id());
        boolean valid = !sessions.isEmpty() && (input.ticket()!=null
                ? tickets.valid(request,input.ticket(),id,(String)sessions.getFirst().get("qr_secret"))
                : input.pin() != null ? input.pin().equals(sessions.getFirst().get("ma_diem_danh"))
                : RotatingQr.matches((String)sessions.getFirst().get("qr_secret"), id, now, input.token()));
        if (!valid) throw AdminController.conflict("Mã sai hoặc hết hạn, hoặc bạn chưa đăng ký lớp.");
        limits.refund(attempt);
        String device = devices.hashForSession(devices.identify(request, response), id);
        try {
            db.update("""
                INSERT INTO diem_danh (buoi_hoc_id, sinh_vien_id, trang_thai, thoi_gian, device_hash)
                VALUES (?, ?, 'CO_MAT', ?, ?)
                """, id, user.id(), now, device);
        } catch(org.springframework.dao.DataAccessException error) {
            var cause=error.getMostSpecificCause();
            if(cause instanceof java.sql.SQLException sql && (sql.getErrorCode()==1062 ||
               ((sql.getErrorCode()&255)==19 && sql.getMessage().toLowerCase(Locale.ROOT).contains("unique"))))
                throw AdminController.conflict("Sinh viên hoặc trình duyệt này đã điểm danh cho buổi học.");
            throw error;
        }
        if(input.ticket()!=null) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { tickets.consume(request,input.ticket()); }
                });
        }
    }

    void requireClass(long id, Account user) {
        int count = db.queryForObject("""
                SELECT COUNT(*) FROM lop_hoc l WHERE l.id = ? AND (
                    ? = 'ADMIN' OR l.giang_vien_id = ? OR EXISTS (
                        SELECT 1 FROM dang_ky WHERE lop_hoc_id = l.id AND sinh_vien_id = ?
                    )
                )
                """, Integer.class, id, user.role(), user.id(), user.id());
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học.");
        }
    }

    record Account(long id, String code, String name, String role) {}
    record SessionInput(LocalDate date, LocalTime start, LocalTime end, String room, String topic, Integer weeks) {}
    record CheckIn(String pin, String token, String ticket) {
        CheckIn(String pin,String token) { this(pin,token,null); }
    }
    record TicketInput(long session,String token) {}
}
