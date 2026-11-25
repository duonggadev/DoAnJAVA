package vn.edu.solop;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.*;
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
class AcademicRegressionTest {
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired LearningController learning;
    @Autowired AttendanceController attendance;
    @Autowired AcademicController academic;
    @Autowired EvidenceController evidence;
    @Autowired GradeWorkbookController workbook;
    @Autowired AdminController admin;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder;
    private final Principal teacher=()->"gv1";
    private final Principal student=()->"sv1";

    @BeforeEach
    void seed() {
        for(String table:new String[]{"thong_bao","nhat_ky_he_thong","minh_chung","nhat_ky_diem","don_xin_phep","diem_danh",
                "ho_so_sinh_vien","chuong_trinh_mon","chuong_trinh","lop_hanh_chinh","khoa_hoc","nganh","khoa",
                "dang_ky","buoi_hoc","lop_hoc","mon_hoc","nguoi_dung","hoc_ky"}) {
            db.update("DELETE FROM "+table);
        }
        db.update("""
                INSERT INTO nguoi_dung(id,ma_so,ho_ten,mat_khau,vai_tro) VALUES
                (1,'gv1','GV 1','test','GIANG_VIEN'),(2,'gv2','GV 2','test','GIANG_VIEN'),
                (3,'sv1','SV 1','test','SINH_VIEN'),(9,'admin','Admin','test','ADMIN')
                """);
        db.update("INSERT INTO mon_hoc(id,ma_mon,ten_mon,so_tin_chi) VALUES (1,'M1','Mon 1',3),(2,'M2','Mon 2',3)");
        db.update("""
                INSERT INTO lop_hoc(id,ma_lop,mon_hoc_id,giang_vien_id,hoc_ky) VALUES
                (1,'L1',1,1,'2026-1'),(2,'L2',2,2,'2026-1')
                """);
        db.update("INSERT INTO dang_ky(id,lop_hoc_id,sinh_vien_id,ngay_dang_ky) VALUES (1,1,3,'2026-11-01')");
    }

    private void session(long id,long classId,String date,String start,String end,String room) {
        db.update("""
                INSERT INTO buoi_hoc(id,lop_hoc_id,ngay_hoc,gio_bat_dau,gio_ket_thuc,phong) VALUES (?,?,?,?,?,?)
                """,id,classId,date,start,end,room);
    }

    @Test
    void attendancePolicyBlocksExamAndFinalizationSnapshotsThenReopenRecalculates() {
        session(1,1,"2026-11-17","08:00:00","09:00:00","A1");
        assertEquals(0,((Number)learning.grades(1,teacher).getFirst().get("eligible")).intValue());
        assertThrows(ResponseStatusException.class,()->learning.editGrades(1,new LearningController.Grades(BigDecimal.TEN,BigDecimal.TEN),teacher));
        academic.finalizeGrades(1,teacher);
        assertEquals(1,db.queryForObject("SELECT bi_cam_thi FROM dang_ky WHERE id=1",Integer.class));
        assertEquals(0,db.queryForObject("SELECT diem_tong_ket FROM dang_ky WHERE id=1",Integer.class));
        assertThrows(ResponseStatusException.class,()->admin.editSubject(1,new AdminController.SubjectInput("M1","Mon 1",4,null,10,30,60)));
        assertThrows(ResponseStatusException.class,()->academic.reopenGrades(1,new AcademicController.ReopenInput(" "),()->"admin"));
        academic.reopenGrades(1,new AcademicController.ReopenInput("Đính chính chuyên cần"),()->"admin");
        assertNull(db.queryForObject("SELECT diem_tong_ket FROM dang_ky WHERE id=1",Object.class));
        assertEquals(2,academic.gradeHistory(1,teacher).size());
        db.update("INSERT INTO diem_danh(buoi_hoc_id,sinh_vien_id,trang_thai) VALUES (1,3,'CO_MAT')");
        learning.editGrades(1,new LearningController.Grades(new BigDecimal("8"),new BigDecimal("9")),teacher);
        academic.finalizeGrades(1,teacher);
        assertEquals("A",db.queryForObject("SELECT diem_chu FROM dang_ky WHERE id=1",String.class));
        assertEquals(8.8,db.queryForObject("SELECT diem_tong_ket FROM dang_ky WHERE id=1",Double.class),0.001);
        assertEquals(0,db.queryForObject("SELECT bi_cam_thi FROM dang_ky WHERE id=1",Integer.class));
        assertThrows(ResponseStatusException.class,()->academic.gradeHistory(1,student));
    }

    @Test
    void cannotCreateOrRescheduleIntoEndedTimeSlot() {
        var past=new AttendanceController.SessionInput(LocalDate.of(2026,11,18),LocalTime.of(8,0),LocalTime.of(9,0),"A1",null,1);
        assertThrows(ResponseStatusException.class,()->attendance.createSession(1,past,teacher));
        session(1,1,"2026-11-19","08:00:00","09:00:00","A1");
        assertThrows(ResponseStatusException.class,()->learning.editSession(1,past,teacher));
        assertEquals("2026-11-19",db.queryForObject("SELECT ngay_hoc FROM buoi_hoc WHERE id=1",String.class));
    }

    @Test
    void lecturerReassignmentCannotCreateTeachingConflict() {
        session(1,1,"2026-11-19","08:00:00","09:00:00","A1");
        session(2,2,"2026-11-19","08:30:00","09:30:00","B1");
        assertThrows(ResponseStatusException.class,()->admin.editClass(1,new AdminController.ClassInput("L1",1,2,"2026-1",60)));
        assertEquals(1,db.queryForObject("SELECT giang_vien_id FROM lop_hoc WHERE id=1",Integer.class));
        db.update("UPDATE buoi_hoc SET gio_bat_dau='09:00:00',gio_ket_thuc='10:00:00' WHERE id=2");
        admin.editClass(1,new AdminController.ClassInput("L1",1,2,"2026-1",60));
    }

    @Test
    void duplicateSubjectSectionIsRejectedAndClosedClassIsNotAdvertisedAsOpen() {
        db.update("UPDATE lop_hoc SET mon_hoc_id=1 WHERE id=2");
        assertThrows(ResponseStatusException.class,()->learning.register(2,student));
        assertEquals(1,((Number)learning.enrollment(student).stream().filter(row->((Number)row.get("id")).intValue()==2).findFirst().orElseThrow().get("sameSubject")).intValue());
        db.update("UPDATE lop_hoc SET da_chot_diem=1 WHERE id=2");
        assertEquals(0,((Number)learning.enrollment(student).stream().filter(row->((Number)row.get("id")).intValue()==2).findFirst().orElseThrow().get("registrationOpen")).intValue());
    }

    @Test
    void exportsSupportBothFormatsAndStudentCannotExportClassData() throws Exception {
        for(String kind:new String[]{"roster","grades","attendance"}) {
            var result=academic.export(1,kind,"xlsx",teacher);
            try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(result.getBody()))) {
                assertEquals("Mã SV",book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
                assertEquals("sv1",book.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            }
            assertTrue(new String(academic.export(1,kind,"csv",teacher).getBody(),java.nio.charset.StandardCharsets.UTF_8).contains("sv1"));
        }
        assertThrows(ResponseStatusException.class,()->academic.export(1,"roster","xlsx",student));
    }

    @Test
    void approvedAbsenceDoesNotLowerEligibility() {
        session(1,1,"2026-11-17","08:00:00","09:00:00","A1");
        db.update("INSERT INTO diem_danh(buoi_hoc_id,sinh_vien_id,trang_thai) VALUES (1,3,'CO_PHEP')");
        assertEquals(1,((Number)learning.grades(1,teacher).getFirst().get("eligible")).intValue());
        learning.editGrades(1,new LearningController.Grades(BigDecimal.TEN,BigDecimal.TEN),teacher);
        academic.finalizeGrades(1,teacher);
        assertEquals("A",db.queryForObject("SELECT diem_chu FROM dang_ky WHERE id=1",String.class));
    }

    @Test
    void evidenceUploadIsAtomicAndRestrictedToItsOwnerAndLecturer() throws Exception {
        session(1,1,"2026-11-19","08:00:00","09:00:00","A1");
        var file=new org.springframework.mock.web.MockMultipartFile("file","note.pdf","application/pdf","%PDF-1.7 test".getBytes());
        evidence.upload(1,"Khám bệnh",file,student);
        String id=db.queryForObject("SELECT id FROM minh_chung",String.class);
        assertArrayEquals(file.getBytes(),evidence.download(id,student).getBody());
        assertArrayEquals(file.getBytes(),evidence.download(id,teacher).getBody());
        assertThrows(ResponseStatusException.class,()->evidence.download(id,()->"gv2"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->evidence.upload(1,"Trùng đơn",file,student));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM minh_chung",Integer.class));
        session(2,1,"2026-11-20","08:00:00","09:00:00","A1");
        var invalid=new org.springframework.mock.web.MockMultipartFile("file","fake.pdf","application/pdf","HTML".getBytes());
        assertThrows(ResponseStatusException.class,()->evidence.upload(2,"Khám bệnh",invalid,student));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM don_xin_phep",Integer.class));
    }

    @Test
    void zipWithoutWorkbookIsRejectedAsUserInput() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("not-a-workbook.txt"));
            zip.write("invalid workbook".getBytes());
            zip.closeEntry();
        }
        var file=new org.springframework.mock.web.MockMultipartFile("file","fake.xlsx","application/octet-stream",bytes.toByteArray());
        var error=assertThrows(ResponseStatusException.class,()->workbook.upload(1,file,teacher));
        assertEquals(400,error.getStatusCode().value());
    }

    @Test
    void excelInvalidCellRollsBackEarlierRows() throws Exception {
        db.update("INSERT INTO nguoi_dung(id,ma_so,ho_ten,mat_khau,vai_tro) VALUES (4,'sv2','SV 2','test','SINH_VIEN')");
        db.update("INSERT INTO dang_ky(id,lop_hoc_id,sinh_vien_id) VALUES (2,1,4)");
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var bytes=new java.io.ByteArrayOutputStream()) {
            var sheet=book.createSheet("Diem");
            var header=sheet.createRow(0);
            String[] titles={"Ma SV","Ho ten","Qua trinh","Thi"};
            for(int i=0;i<4;i++)header.createCell(i).setCellValue(titles[i]);
            var first=sheet.createRow(1);first.createCell(0).setCellValue("sv1");first.createCell(2).setCellValue(8);first.createCell(3).setCellValue(9);
            var second=sheet.createRow(2);second.createCell(0).setCellValue("sv2");second.createCell(2).setCellValue(true);
            book.write(bytes);
            var file=new org.springframework.mock.web.MockMultipartFile("file","grades.xlsx","application/octet-stream",bytes.toByteArray());
            var error=assertThrows(ResponseStatusException.class,()->workbook.upload(1,file,teacher));
            assertEquals(400,error.getStatusCode().value());
            assertNull(db.queryForObject("SELECT diem_qua_trinh FROM dang_ky WHERE id=1",Object.class));
        }
    }

    private org.springframework.mock.web.MockHttpSession login(String user) throws Exception {
        var token=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/csrf")).andReturn();
        var csrf=json.readTree(token.getResponse().getContentAsString());
        var session=(org.springframework.mock.web.MockHttpSession)token.getRequest().getSession();
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/login")
                .session(session).header(csrf.get("header").asText(),csrf.get("token").asText())
                .param("username",user).param("password","Original2026")).andReturn();
        assertEquals(204,result.getResponse().getStatus());
        return (org.springframework.mock.web.MockHttpSession)result.getRequest().getSession();
    }

    @Test
    void passwordChangeRevokesOtherSessionsAndEnforcesRoleBoundary() throws Exception {
        db.update("UPDATE nguoi_dung SET mat_khau=?",encoder.encode("Original2026"));
        var first=login("sv1");var second=login("sv1");
        var token=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/csrf").session(first)).andReturn();
        var csrf=json.readTree(token.getResponse().getContentAsString());
        var denied=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/classes/1/reopen")
                .session(first).header(csrf.get("header").asText(),csrf.get("token").asText())
                .contentType("application/json").content("{\"reason\":\"Forged\"}")).andReturn();
        assertEquals(403,denied.getResponse().getStatus());
        var changed=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/account/password")
                .session(first).header(csrf.get("header").asText(),csrf.get("token").asText())
                .contentType("application/json").content("{\"currentPassword\":\"Original2026\",\"newPassword\":\"Changed2026\"}")).andReturn();
        assertEquals(204,changed.getResponse().getStatus());
        assertTrue(first.isInvalid());
        assertTrue(encoder.matches("Changed2026",db.queryForObject("SELECT mat_khau FROM nguoi_dung WHERE id=3",String.class)));
        assertEquals(401,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me").session(second))
                .andReturn().getResponse().getStatus());
        assertEquals(401,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/leave-evidence/unknown"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void endedTodayCountsAsAbsenceAndPreventsWithdrawal() {
        session(1,1,"2026-11-18","08:00:00","09:00:00","A1");
        var result=academic.attendanceOverview("2026-1");
        assertEquals(1L,result.get("total"));
        assertThrows(ResponseStatusException.class,()->learning.deleteSession(1,teacher));
        var shift=new AttendanceController.SessionInput(LocalDate.of(2026,11,19),LocalTime.of(8,0),LocalTime.of(9,0),"A1",null,1);
        assertThrows(ResponseStatusException.class,()->learning.editSession(1,shift,teacher));
        var ended=(java.util.List<Map<String,Object>>)attendance.classBook(1,teacher).get("sessions");
        assertEquals(0,((Number)ended.getFirst().get("editable")).intValue());
        var roster=(java.util.List<Map<String,Object>>)attendance.classBook(1,teacher).get("roster");
        assertEquals(1,((Number)roster.getFirst().get("absent")).intValue());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->learning.withdraw(1,student)).getStatusCode().value());
    }

    @Test
    void runningSessionCannotBeFinalizedButEndedSessionCan() {
        session(1,1,"2026-11-18","09:00:00","11:00:00","A1");
        learning.editGrades(1,new LearningController.Grades(new BigDecimal("8"),new BigDecimal("9")),teacher);
        assertThrows(ResponseStatusException.class,()->academic.finalizeGrades(1,teacher));
        db.update("UPDATE buoi_hoc SET gio_ket_thuc='09:30:00' WHERE id=1");
        academic.finalizeGrades(1,teacher);
        assertEquals(1,db.queryForObject("SELECT da_chot_diem FROM lop_hoc WHERE id=1",Integer.class));
        assertThrows(ResponseStatusException.class,()->attendance.open(1,teacher));
        assertThrows(ResponseStatusException.class,()->learning.editGrades(1,new LearningController.Grades(BigDecimal.ONE,BigDecimal.ONE),teacher));
    }

    @Test
    void sharedStudentScheduleConflictsAndRecurringCreationRollsBack() {
        db.update("INSERT INTO dang_ky(lop_hoc_id,sinh_vien_id) VALUES (2,3)");
        session(1,2,"2026-11-25","08:00:00","10:00:00","B2");
        var input=new AttendanceController.SessionInput(LocalDate.of(2026,11,18),LocalTime.of(8,0),LocalTime.of(9,0),"A1","topic",2);
        assertThrows(ResponseStatusException.class,()->attendance.createSession(1,input,teacher));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM buoi_hoc WHERE lop_hoc_id=1",Integer.class));
        var adjacent=new AttendanceController.SessionInput(LocalDate.of(2026,11,25),LocalTime.of(10,0),LocalTime.of(11,0),"A1",null,1);
        attendance.createSession(1,adjacent,teacher);
    }

    @Test
    void leaveRequestProtectsSessionAndPendingReviewPreventsFinalizing() {
        session(1,1,"2026-11-19","08:00:00","09:00:00","A1");
        academic.requestLeave(1,new AcademicController.LeaveInput("Khám bệnh",null),student);
        var change=new AttendanceController.SessionInput(LocalDate.of(2026,11,20),LocalTime.of(8,0),LocalTime.of(9,0),"A1",null,1);
        assertThrows(ResponseStatusException.class,()->learning.editSession(1,change,teacher));
        assertThrows(ResponseStatusException.class,()->learning.deleteSession(1,teacher));
        db.update("UPDATE buoi_hoc SET ngay_hoc='2026-11-17' WHERE id=1");
        learning.editGrades(1,new LearningController.Grades(BigDecimal.TEN,null),teacher);
        assertThrows(ResponseStatusException.class,()->academic.finalizeGrades(1,teacher));
    }

    @Test
    void qrContextAndRotatingTokenRoundTrip() {
        session(1,1,"2026-11-18","10:00:00","11:00:00","A1");
        attendance.open(1,teacher);
        assertEquals(1,((Number)attendance.checkInContext(1,student).get("open")).intValue());
        var payload=attendance.qr(1,teacher).getBody();
        assertNotNull(payload);
        String token=(String)payload.get("token");
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        assertThrows(ResponseStatusException.class,()->attendance.checkIn(1,
                new AttendanceController.CheckIn(null,"A".repeat(43)),student,request,response));
        attendance.checkIn(1,new AttendanceController.CheckIn(null,token),student,request,response);
        assertEquals("CO_MAT",attendance.checkInContext(1,student).get("status"));
        assertThrows(ResponseStatusException.class,()->attendance.checkIn(1,
                new AttendanceController.CheckIn(null,token),student,request,response));
        learning.closeSession(1,teacher);
        assertEquals(0,((Number)attendance.checkInContext(1,student).get("open")).intValue());
        assertThrows(ResponseStatusException.class,()->attendance.qr(1,teacher));
        db.update("INSERT INTO nguoi_dung(id,ma_so,ho_ten,mat_khau,vai_tro) VALUES (4,'sv2','SV2','test','SINH_VIEN')");
        assertEquals(404,assertThrows(ResponseStatusException.class,()->attendance.checkInContext(1,()->"sv2")).getStatusCode().value());
        db.update("INSERT INTO dang_ky(lop_hoc_id,sinh_vien_id) VALUES (1,4)");
        attendance.open(1,teacher);
        String reopenedToken=(String)attendance.qr(1,teacher).getBody().get("token");
        assertNotEquals(token,reopenedToken);
        assertEquals("CO_MAT",attendance.checkInContext(1,student).get("status"));
        assertThrows(ResponseStatusException.class,()->attendance.checkIn(1,
                new AttendanceController.CheckIn(null,token),()->"sv2",request,response));
        attendance.checkIn(1,new AttendanceController.CheckIn(null,reopenedToken),()->"sv2",request,response);
        assertEquals("CO_MAT",attendance.checkInContext(1,()->"sv2").get("status"));
    }

    @Test
    void scanProofRequiresFreshLoginAndStaysBoundToBrowserAndQrGeneration() {
        Clock now=Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
        var proofs=new QrTickets(now);
        var browser=new org.springframework.mock.web.MockHttpServletRequest();
        browser.getSession().setAttribute("solop.loginVersion","old-login");
        String proof=proofs.issue(browser,1,"generation-1");
        assertFalse(proofs.valid(browser,proof,1,"generation-1"));
        browser.getSession().setAttribute("solop.loginVersion","new-login");
        assertTrue(proofs.valid(browser,proof,1,"generation-1"));
        assertFalse(proofs.valid(new org.springframework.mock.web.MockHttpServletRequest(),proof,1,"generation-1"));
        assertFalse(proofs.valid(browser,proof,2,"generation-1"));
        assertFalse(proofs.valid(browser,proof,1,"generation-2"));
        assertFalse(new QrTickets(Clock.offset(now,Duration.ofMinutes(3))).valid(browser,proof,1,"generation-1"));
        proofs.consume(browser,proof);
        assertFalse(proofs.valid(browser,proof,1,"generation-1"));
    }

    @Test
    void rotatingQrRejectsOldTokens() {
        String secret=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        long now=80_001;
        assertTrue(RotatingQr.matches(secret,1,now,RotatingQr.current(secret,1,now)));
        assertTrue(RotatingQr.matches(secret,1,now,RotatingQr.current(secret,1,now-20_000)));
        assertFalse(RotatingQr.matches(secret,1,now+11000,RotatingQr.current(secret,1,now-20_000)));
        assertFalse(RotatingQr.matches(secret,2,now,RotatingQr.current(secret,1,now)));
    }

    @Test
    void enrollmentRejectsCapacityConflictAndClosedTerm() {
        session(1,1,"2026-11-19","08:00:00","09:00:00","A1");
        session(2,2,"2026-11-19","08:30:00","09:30:00","B1");
        assertThrows(ResponseStatusException.class,()->learning.register(2,student));
        db.update("DELETE FROM buoi_hoc WHERE id=2");
        db.update("INSERT INTO nguoi_dung(id,ma_so,ho_ten,mat_khau,vai_tro) VALUES (4,'sv2','SV 2','test','SINH_VIEN')");
        db.update("INSERT INTO dang_ky(id,lop_hoc_id,sinh_vien_id) VALUES (99,2,4)");
        db.update("UPDATE lop_hoc SET si_so_toi_da=1 WHERE id=2");
        assertThrows(ResponseStatusException.class,()->learning.register(2,student));
        db.update("DELETE FROM dang_ky WHERE id=99");
        db.update("DELETE FROM nguoi_dung WHERE id=4");
        db.update("UPDATE lop_hoc SET si_so_toi_da=60 WHERE id=2");
        db.update("INSERT INTO hoc_ky VALUES ('2026-1','2026-01-01','2026-02-01',80)");
        assertThrows(ResponseStatusException.class,()->learning.register(2,student));
    }
}