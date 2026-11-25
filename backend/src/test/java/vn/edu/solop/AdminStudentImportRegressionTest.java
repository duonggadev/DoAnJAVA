package vn.edu.solop;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.profiles.active=test","solop.demo=false"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class AdminStudentImportRegressionTest {
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
        for (String table : new String[]{"thong_bao", "nhat_ky_he_thong", "minh_chung", "nhat_ky_diem",
                "don_xin_phep", "diem_danh", "buoi_hoc", "dang_ky", "lop_hoc", "mon_hoc",
                "ho_so_sinh_vien", "chuong_trinh_mon", "chuong_trinh", "lop_hanh_chinh",
                "khoa_hoc", "nganh", "khoa", "nguoi_dung"}) {
            db.update("DELETE FROM " + table);
        }
        db.update("""
                INSERT INTO nguoi_dung(id,ma_so,ho_ten,mat_khau,vai_tro) VALUES
                (1,'admin','Admin','test','ADMIN'),(2,'gv1','GV 1','test','GIANG_VIEN'),
                (3,'sv1','SV 1','test','SINH_VIEN')
                """);
        db.update("INSERT INTO khoa(id,ma,ten) VALUES (1,'K1','Khoa 1')");
        db.update("INSERT INTO nganh(id,ma,ten,khoa_id) VALUES (1,'N1','Nganh 1',1)");
        db.update("INSERT INTO khoa_hoc(id,ma,ten,nam_bat_dau,nam_ket_thuc) VALUES (1,'KH1','Khoa 1',2024,2028)");
        db.update("INSERT INTO lop_hanh_chinh(id,ma,ten,nganh_id,khoa_hoc_id) VALUES (1,'LHC1','Lop 1',1,1)");
        db.update("INSERT INTO chuong_trinh(id,ma,ten,nganh_id,so_tin_chi) VALUES (1,'CT1','Chuong trinh 1',1,120)");
        db.update("INSERT INTO mon_hoc(id,ma_mon,ten_mon,so_tin_chi) VALUES (1,'M1','Mon 1',3)");
        db.update("INSERT INTO lop_hoc(id,ma_lop,mon_hoc_id,giang_vien_id,hoc_ky,si_so_toi_da) VALUES (1,'L1',1,2,'2026-1',3)");
    }

    private AdminController.UserInput student(String code, Long groupId) {
        return new AdminController.UserInput(code, "SV " + code, code + "@example.com", "SINH_VIEN",
                "matkhau123", false, groupId, "2004-05-06", "NU", "0905123456", "DANG_HOC", 1L);
    }

    @Test
    void createStudentWithProfileCreatesAccountProfileAndNotification() {
        admin.createUser(student("sv10", 1L));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM ho_so_sinh_vien WHERE sinh_vien_id=(SELECT id FROM nguoi_dung WHERE ma_so='sv10')", Integer.class));
        assertEquals("DANG_HOC", db.queryForObject("SELECT trang_thai FROM ho_so_sinh_vien WHERE sinh_vien_id=(SELECT id FROM nguoi_dung WHERE ma_so='sv10')", String.class));
        assertEquals("2004-05-06", db.queryForObject("SELECT ngay_sinh FROM ho_so_sinh_vien WHERE sinh_vien_id=(SELECT id FROM nguoi_dung WHERE ma_so='sv10')", String.class));
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM thong_bao WHERE nguoi_dung_id=(SELECT id FROM nguoi_dung WHERE ma_so='sv10')", Integer.class) > 0);
    }

    @Test
    void plainStudentCreationStillWorksWithoutProfile() {
        admin.createUser(new AdminController.UserInput("sv11", "SV Thu", null, "SINH_VIEN", "matkhau123",
                false, null, null, null, null, null, null));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM nguoi_dung WHERE ma_so='sv11'", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM ho_so_sinh_vien WHERE sinh_vien_id=(SELECT id FROM nguoi_dung WHERE ma_so='sv11')", Integer.class));
    }

    @Test
    void invalidRowRejectsEntireImportBatch() {
        var result = admin.importStudents(new AdminController.ImportInput(List.of(student("sv20", 1L), student("sv21", null))));
        assertEquals(0, ((Number) result.get("created")).intValue());
        @SuppressWarnings("unchecked") var errors = (List<Map<String, Object>>) result.get("errors");
        assertEquals(1, errors.size());
        assertEquals(2, ((Number) errors.get(0).get("row")).intValue());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM nguoi_dung WHERE ma_so IN ('sv20','sv21')", Integer.class));
    }

    @Test
    void duplicateCodesWithinBatchAreRejected() {
        var result = admin.importStudents(new AdminController.ImportInput(List.of(student("sv22", 1L), student("sv22", 1L))));
        assertEquals(0, ((Number) result.get("created")).intValue());
        @SuppressWarnings("unchecked") var errors = (List<Map<String, Object>>) result.get("errors");
        assertEquals(1, errors.size());
        assertEquals(2, ((Number) errors.get(0).get("row")).intValue());
    }

    @Test
    void validImportCreatesAccountsProfilesAndNotifications() {
        var result = admin.importStudents(new AdminController.ImportInput(List.of(student("sv30", 1L), student("sv31", 1L))));
        assertEquals(2, ((Number) result.get("created")).intValue());
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM ho_so_sinh_vien WHERE sinh_vien_id IN (SELECT id FROM nguoi_dung WHERE ma_so IN ('sv30','sv31'))", Integer.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM thong_bao WHERE nguoi_dung_id IN (SELECT id FROM nguoi_dung WHERE ma_so IN ('sv30','sv31'))", Integer.class));
    }

    @Test
    void rosterChangesNotifyTheStudent() {
        admin.enrollStudent(1, new AdminController.RosterInput(3L));
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM thong_bao WHERE nguoi_dung_id=3 AND tieu_de='Phân lớp học phần'", Integer.class) > 0);
        admin.unenrollStudent(1, 3L);
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM thong_bao WHERE nguoi_dung_id=3 AND tieu_de='Điều chỉnh lớp học phần'", Integer.class) > 0);
    }
}
