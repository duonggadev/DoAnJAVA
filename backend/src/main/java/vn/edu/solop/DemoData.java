package vn.edu.solop;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "solop.demo", havingValue = "true", matchIfMissing = true)
class DemoData implements ApplicationRunner {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder passwords;
    private final Clock clock;

    DemoData(JdbcTemplate db, BCryptPasswordEncoder passwords, Clock clock) {
        this.db = db;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (db.queryForObject("SELECT COUNT(*) FROM nguoi_dung", Integer.class) > 0) {
            return;
        }
        List<String> names = List.of("Ban đào tạo", "Trần Minh Anh", "Lê Gia Hân", "Nguyễn Hoàng Nam",
                "Phạm Thảo Linh", "Vũ Đức Khang", "Đỗ Khánh Vy", "Bùi Nhật Minh");
        for (int i = 0; i < names.size(); i++) {
            String code = i == 0 ? "admin" : i == 1 ? "gv01" : "sv0" + (i - 1);
            String role = i == 0 ? "ADMIN" : i == 1 ? "GIANG_VIEN" : "SINH_VIEN";
            db.update("INSERT INTO nguoi_dung (id, ma_so, ho_ten, mat_khau, vai_tro) VALUES (?,?,?,?,?)",
                    i + 1, code, names.get(i), passwords.encode("DemoSoLop2026"), role);
        }
        db.update("INSERT INTO mon_hoc (id, ma_mon, ten_mon, so_tin_chi) VALUES (1, 'IT204', 'Cơ sở dữ liệu', 3), (2, 'IT306', 'Lập trình Java', 3)");
        db.update("INSERT INTO lop_hoc (id, ma_lop, mon_hoc_id, giang_vien_id, hoc_ky) VALUES (1, 'IT204.01', 1, 2, '2026-1'), (2, 'IT306.02', 2, 2, '2026-1')");
        for (int id = 3; id <= 8; id++) {
            db.update("INSERT INTO dang_ky (lop_hoc_id, sinh_vien_id) VALUES (1, ?), (2, ?)", id, id);
        }
        LocalDate today = LocalDate.now(clock);
        for (int i = 1; i <= 5; i++) {
            int classId = i == 5 ? 2 : 1;
            int days = switch (i) {
                case 1 -> -14;
                case 2 -> -7;
                case 4 -> 7;
                default -> 0;
            };
            db.update("INSERT INTO buoi_hoc (id, lop_hoc_id, ngay_hoc, gio_bat_dau, gio_ket_thuc, phong) VALUES (?,?,?,?,?,?)",
                    i, classId, today.plusDays(days).toString(), "08:00", "09:40", classId == 1 ? "B.203" : "A.405");
        }
        db.update("""
                INSERT INTO diem_danh (buoi_hoc_id, sinh_vien_id, trang_thai)
                SELECT b.id, d.sinh_vien_id,
                    CASE WHEN d.sinh_vien_id = 8 AND b.id = 2 THEN 'VANG'
                         WHEN d.sinh_vien_id = 4 AND b.id = 1 THEN 'DI_MUON'
                         ELSE 'CO_MAT' END
                FROM dang_ky d JOIN buoi_hoc b ON b.lop_hoc_id = d.lop_hoc_id WHERE b.id IN (1, 2)
                """);
    }
}
