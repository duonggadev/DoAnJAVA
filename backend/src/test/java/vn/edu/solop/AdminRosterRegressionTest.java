package vn.edu.solop;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.profiles.active=test","solop.demo=false"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class AdminRosterRegressionTest {
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired AdminController admin;

    @BeforeEach
    void seed() {
        for(String table:new String[]{"thong_bao","nhat_ky_he_thong","minh_chung","nhat_ky_diem","don_xin_phep","diem_danh",
                "ho_so_sinh_vien","chuong_trinh_mon","chuong_trinh","lop_hanh_chinh","khoa_hoc","nganh","khoa",
                "dang_ky","buoi_hoc","lop_hoc","mon_hoc","nguoi_dung","hoc_ky"}) {
            db.update("DELETE FROM "+table);
        }
        db.update("""
                INSERT INTO nguoi_dung(id,ma_so,ho_ten,mat_khau,vai_tro) VALUES
                (1,'gv1','GV 1','test','GIANG_VIEN'),
                (2,'sv1','SV 1','test','SINH_VIEN'),
                (3,'sv2','SV 2','test','SINH_VIEN'),
                (4,'sv3','SV 3','test','SINH_VIEN')
                """);
        db.update("INSERT INTO mon_hoc(id,ma_mon,ten_mon,so_tin_chi) VALUES (1,'M1','Mon 1',3),(2,'M2','Mon 2',3)");
        db.update("""
                INSERT INTO lop_hoc(id,ma_lop,mon_hoc_id,giang_vien_id,hoc_ky,si_so_toi_da) VALUES
                (1,'L1',1,1,'2026-1',3),(2,'L2',2,1,'2026-1',3),(3,'L3',1,1,'2026-1',3)
                """);
        db.update("INSERT INTO dang_ky(id,lop_hoc_id,sinh_vien_id,ngay_dang_ky) VALUES (1,1,2,'2026-11-01'),(2,1,3,'2026-11-01')");
    }

    @Test
    void catalogReportsEnrollmentCountPerClass() {
        var classes = (java.util.List<Map<String,Object>>)admin.catalog().get("classes");
        var first = classes.stream().filter(row -> ((Number)row.get("id")).longValue() == 1).findFirst().orElseThrow();
        assertEquals(2, ((Number)first.get("enrollment")).intValue());
        assertEquals(3, ((Number)first.get("capacity")).intValue());
    }

    @Test
    void rosterAddsAndRemovesStudent() {
        assertEquals(2, admin.roster(1).size());
        admin.enrollStudent(1, new AdminController.RosterInput(4L));
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM dang_ky WHERE lop_hoc_id=1", Integer.class));
        assertEquals("sv3", admin.roster(1).getLast().get("code"));
        admin.unenrollStudent(1, 4L);
        assertEquals(2, admin.roster(1).size());
    }

    @Test
    void enrollmentRejectsDuplicateFullClassAndSameSubject() {
        assertThrows(ResponseStatusException.class, () -> admin.enrollStudent(1, new AdminController.RosterInput(2L)));
        assertThrows(ResponseStatusException.class, () -> admin.enrollStudent(1, new AdminController.RosterInput(99L)));
        db.update("UPDATE lop_hoc SET si_so_toi_da=2 WHERE id=1");
        assertThrows(ResponseStatusException.class, () -> admin.enrollStudent(1, new AdminController.RosterInput(4L)));
        db.update("UPDATE lop_hoc SET si_so_toi_da=3 WHERE id=1");
        admin.enrollStudent(3, new AdminController.RosterInput(4L));
        assertThrows(ResponseStatusException.class, () -> admin.enrollStudent(1, new AdminController.RosterInput(4L)));
    }

    @Test
    void cannotRemoveStudentWithAttendanceOrFinishedSession() {
        db.update("INSERT INTO buoi_hoc(id,lop_hoc_id,ngay_hoc,gio_bat_dau,gio_ket_thuc,phong) VALUES (1,1,'2026-11-17','08:00:00','09:00:00','A1')");
        db.update("INSERT INTO diem_danh(buoi_hoc_id,sinh_vien_id,trang_thai) VALUES (1,2,'CO_MAT')");
        assertThrows(ResponseStatusException.class, () -> admin.unenrollStudent(1, 2L));
        assertThrows(ResponseStatusException.class, () -> admin.unenrollStudent(1, 3L));
        assertEquals(2, admin.roster(1).size());
    }
}
