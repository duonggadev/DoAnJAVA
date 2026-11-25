package vn.edu.solop;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"spring.profiles.active=test","solop.demo=false","solop.require-term=false"})
@AutoConfigureMockMvc
class OperationsRegressionTest {
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock operationsClock() {
            return Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
    @Autowired JdbcTemplate db;
    @Autowired OperationsStore store;
    @Autowired OperationsController operations;
    @Autowired LearningController learning;
    @Autowired AttendanceController attendance;
    @Autowired AdminController admin;
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder;
    private final Principal student=()->"sv1";
    private final Principal otherStudent=()->"sv2";
    private final Principal teacher=()->"gv1";

    @BeforeEach
    void seed() {
        for(String table:new String[]{"thong_bao","nhat_ky_he_thong","chuong_trinh_mon","ho_so_sinh_vien","chuong_trinh",
                "lop_hanh_chinh","khoa_hoc","nganh","khoa","minh_chung","nhat_ky_diem","don_xin_phep","diem_danh",
                "dang_ky","buoi_hoc","lop_hoc","mon_hoc","nguoi_dung","hoc_ky"})db.update("DELETE FROM "+table);
        String hash=encoder.encode("Student2026");
        db.update("INSERT INTO nguoi_dung(id,ma_so,ho_ten,mat_khau,vai_tro) VALUES (1,'admin','Admin',?,'ADMIN'),(2,'gv1','GV 1',?,'GIANG_VIEN'),(3,'sv1','SV 1',?,'SINH_VIEN'),(4,'sv2','SV 2',?,'SINH_VIEN')",hash,hash,hash,hash);
        db.update("INSERT INTO mon_hoc(id,ma_mon,ten_mon,so_tin_chi) VALUES (1,'M1','Mon 1',3)");
        db.update("INSERT INTO lop_hoc(id,ma_lop,mon_hoc_id,giang_vien_id,hoc_ky) VALUES (1,'L1',1,2,'2026-1')");
        db.update("INSERT INTO buoi_hoc(id,lop_hoc_id,ngay_hoc,gio_bat_dau,gio_ket_thuc,phong) VALUES (1,1,'2026-11-18','10:00:00','11:00:00','A1')");
        db.update("INSERT INTO khoa(id,ma,ten) VALUES (1,'CNTT','Cong nghe thong tin')");
        db.update("INSERT INTO nganh(id,ma,ten,khoa_id) VALUES (1,'PM','Phan mem',1)");
        db.update("INSERT INTO khoa_hoc(id,ma,ten,nam_bat_dau,nam_ket_thuc) VALUES (1,'K26','Khoa 2026',2026,2030)");
        db.update("INSERT INTO lop_hanh_chinh(id,ma,ten,nganh_id,khoa_hoc_id) VALUES (1,'K26.PM1','Phan mem 1',1,1)");
        db.update("INSERT INTO ho_so_sinh_vien(sinh_vien_id,lop_hanh_chinh_id,trang_thai) VALUES (3,1,'DANG_HOC')");
    }

    @Test
    void notificationPaginationReadAndReadAllStayWithinOwnerAndAreIdempotent() {
        store.notifyUser(3,"Đơn nghỉ","Giảng viên đã xử lý đơn.");
        store.notifyUser(3,"Bảng điểm","Bảng điểm đã chốt.");
        store.notifyUser(3,"Lịch học","Lịch buổi học đã thay đổi.");
        store.notifyUser(4,"Riêng tư","Chỉ sinh viên thứ hai được đọc.");
        var page=operations.notifications(1,2,student);
        assertEquals(3L,page.get("total"));
        assertEquals(3L,page.get("unread"));
        assertEquals(2,((List<?>)page.get("items")).size());
        assertEquals(1,((List<?>)operations.notifications(2,2,student).get("items")).size());
        String own=db.queryForObject("SELECT id FROM thong_bao WHERE nguoi_dung_id=3 AND tieu_de='Đơn nghỉ'",String.class);
        String foreign=db.queryForObject("SELECT id FROM thong_bao WHERE nguoi_dung_id=4",String.class);
        var denied=assertThrows(ResponseStatusException.class,()->operations.read(foreign,student));
        assertEquals(404,denied.getStatusCode().value());
        operations.read(own,student);
        operations.read(own,student);
        assertEquals(2L,operations.notifications(1,10,student).get("unread"));
        operations.readAll(student);
        operations.readAll(student);
        assertEquals(0L,operations.notifications(1,10,student).get("unread"));
        assertEquals(1L,operations.notifications(1,10,otherStudent).get("unread"));
        assertThrows(ResponseStatusException.class,()->operations.notifications(0,10,student));
        assertThrows(ResponseStatusException.class,()->operations.notifications(1,101,student));
    }

    @Test
    void requestAuditCapturesLoginAndDeniedMutationWithoutPersistingSecrets() throws Exception {
        var failed=postLogin("sv1","BodySecretCanary42","?access_token=QuerySecretCanary42");
        assertEquals(401,failed.getResponse().getStatus());
        var success=postLogin("sv1","Student2026","");
        assertEquals(204,success.getResponse().getStatus());
        var session=(MockHttpSession)success.getRequest().getSession();
        var token=mvc.perform(get("/api/csrf").session(session)).andReturn();
        var csrf=json.readTree(token.getResponse().getContentAsString());
        var denied=mvc.perform(post("/api/admin/subjects?api_key=QuerySecretCanary42").session(session)
                .header(csrf.get("header").asText(),csrf.get("token").asText()).contentType("application/json")
                .content("{\"code\":\"UNAUTHORIZED\",\"password\":\"BodySecretCanary42\"}")).andReturn();
        assertEquals(403,denied.getResponse().getStatus());
        for(MvcResult result:List.of(failed,success,denied)) {
            String request=result.getResponse().getHeader("X-Request-Id");
            assertNotNull(request);
            assertTrue(request.matches("[a-f0-9-]{36}"));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM nhat_ky_he_thong WHERE ma_yeu_cau=? AND ma_phan_hoi=?",Integer.class,request,result.getResponse().getStatus()));
        }
        var rows=db.queryForList("SELECT * FROM nhat_ky_he_thong");
        assertEquals(3,rows.size());
        assertTrue(rows.stream().allMatch(row->"sv1".equals(row.get("tai_khoan"))));
        String persisted=rows.toString();
        assertFalse(persisted.contains("BodySecretCanary42"));
        assertFalse(persisted.contains("QuerySecretCanary42"));
        assertFalse(persisted.contains("Student2026"));
        assertFalse(persisted.contains("UNAUTHORIZED"));
        assertFalse(persisted.contains("?"));
        var page=operations.audit(1,2,"sv1");
        assertEquals(3L,page.get("total"));
        assertEquals(2,((List<?>)page.get("items")).size());
        assertEquals(0L,operations.audit(1,20,"QuerySecretCanary42").get("total"));
        assertThrows(ResponseStatusException.class,()->operations.audit(1,101,""));
    }

    @Test
    void inactiveAcademicStatusBlocksBothRegistrationAndAttendance() {
        db.update("UPDATE ho_so_sinh_vien SET trang_thai='BAO_LUU' WHERE sinh_vien_id=3");
        var registration=assertThrows(ResponseStatusException.class,()->learning.register(1,student));
        assertEquals(409,registration.getStatusCode().value());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dang_ky",Integer.class));
        db.update("INSERT INTO dang_ky(lop_hoc_id,sinh_vien_id,ngay_dang_ky) VALUES (1,3,'2026-11-01')");
        attendance.open(1,teacher);
        String pin=db.queryForObject("SELECT ma_diem_danh FROM buoi_hoc WHERE id=1",String.class);
        var denied=assertThrows(ResponseStatusException.class,()->attendance.checkIn(1,new AttendanceController.CheckIn(pin,null),student,
                new MockHttpServletRequest(),new MockHttpServletResponse()));
        assertEquals(409,denied.getStatusCode().value());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM diem_danh",Integer.class));
    }

    @Test
    void activeProfileCanRegisterAttendAndReceiveOwnNotification() {
        learning.register(1,student);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dang_ky WHERE sinh_vien_id=3",Integer.class));
        attendance.open(1,teacher);
        String pin=db.queryForObject("SELECT ma_diem_danh FROM buoi_hoc WHERE id=1",String.class);
        attendance.checkIn(1,new AttendanceController.CheckIn(pin,null),student,new MockHttpServletRequest(),new MockHttpServletResponse());
        assertEquals("CO_MAT",db.queryForObject("SELECT trang_thai FROM diem_danh WHERE buoi_hoc_id=1 AND sinh_vien_id=3",String.class));
        assertTrue(((Number)operations.notifications(1,10,student).get("total")).longValue()>0);
        assertEquals(0L,operations.notifications(1,10,otherStudent).get("total"));
    }

    @Test
    void studentProfilePreventsRoleChangeBeforeAnyCourseRegistration() {
        var change=new AdminController.UserInput("sv1","SV 1",null,"GIANG_VIEN",null,false,null,null,null,null,null,null);
        var denied=assertThrows(ResponseStatusException.class,()->admin.editUser(3,change,()->"admin"));
        assertEquals(409,denied.getStatusCode().value());
        assertEquals("SINH_VIEN",db.queryForObject("SELECT vai_tro FROM nguoi_dung WHERE id=3",String.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dang_ky",Integer.class));
    }

    private MvcResult postLogin(String code,String password,String query) throws Exception {
        var result=mvc.perform(get("/api/csrf")).andReturn();
        var csrf=json.readTree(result.getResponse().getContentAsString());
        return mvc.perform(post("/api/login"+query).session((MockHttpSession)result.getRequest().getSession())
                .header(csrf.get("header").asText(),csrf.get("token").asText()).param("username",code).param("password",password)).andReturn();
    }
}
