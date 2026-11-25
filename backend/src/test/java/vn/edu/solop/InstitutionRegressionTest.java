package vn.edu.solop;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.profiles.active=test","solop.demo=false","solop.require-term=false"})
@AutoConfigureMockMvc
class InstitutionRegressionTest {
    @Autowired JdbcTemplate db;
    @Autowired InstitutionController institution;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder;
    private final Principal student=()->"sv1";

    @BeforeEach
    void seed() {
        for(String table:new String[]{"chuong_trinh_mon","ho_so_sinh_vien","chuong_trinh","lop_hanh_chinh","khoa_hoc","nganh","khoa",
                "thong_bao","nhat_ky_he_thong","minh_chung","nhat_ky_diem","don_xin_phep","diem_danh","dang_ky","buoi_hoc","lop_hoc","mon_hoc","nguoi_dung","hoc_ky"})
            db.update("DELETE FROM "+table);
        for(String table:new String[]{"khoa","nganh","khoa_hoc","lop_hanh_chinh","chuong_trinh","chuong_trinh_mon",
                "lop_hoc","buoi_hoc","mon_hoc","nguoi_dung"})
            db.execute("ALTER TABLE "+table+" AUTO_INCREMENT=1");
        db.update("""
                INSERT INTO nguoi_dung(id,ma_so,ho_ten,mat_khau,vai_tro) VALUES
                (1,'admin','Admin','test','ADMIN'),(2,'gv1','GV 1','test','GIANG_VIEN'),
                (3,'sv1','SV 1','test','SINH_VIEN'),(4,'sv2','SV 2','test','SINH_VIEN')
                """);
        db.update("INSERT INTO mon_hoc(id,ma_mon,ten_mon,so_tin_chi) VALUES (1,'M1','Mon 1',3),(2,'M2','Mon 2',3)");
    }

    private InstitutionController.Input input(Map<String,Object> body) { return json.convertValue(body,InstitutionController.Input.class); }
    private long create(String entity,Map<String,Object> body) { return ((Number)institution.create(entity,input(body)).get("id")).longValue(); }

    private long group() {
        long department=create("departments",Map.of("code","CNTT","name","Công nghệ thông tin"));
        long major=create("majors",Map.of("code","KTPM","name","Kỹ thuật phần mềm","departmentId",department));
        long cohort=create("cohorts",Map.of("code","K26","name","Khóa 2026","startYear",2026,"endYear",2030));
        return create("groups",Map.of("code","K26.PM1","name","Phần mềm 1","majorId",major,"cohortId",cohort));
    }

    @Test
    void relationalCatalogAndBoundedSearchPaginationWork() {
        long group=group();
        create("departments",Map.of("code","KT","name","Kinh tế 100%"));
        var catalog=institution.catalog();
        assertEquals(1,((List<?>)catalog.get("groups")).size());
        assertEquals(2,((List<?>)catalog.get("students")).size());
        var page=institution.list("departments",1,1,"");
        assertEquals(2L,page.get("total"));
        assertEquals(1,((List<?>)page.get("items")).size());
        assertEquals(1L,institution.list("departments",1,20,"%").get("total"));
        assertEquals(0L,institution.list("departments",1,20,"_SURPRISE_").get("total"));
        assertThrows(ResponseStatusException.class,()->institution.list("departments",0,20,""));
        assertThrows(ResponseStatusException.class,()->institution.list("departments",1,101,""));
        assertThrows(ResponseStatusException.class,()->institution.list("nguoi_dung",1,20,""));
        create("profiles",Map.of("studentId",3,"groupId",group,"birthDate","2006-03-12","gender","NU","phone","0901234567","status","DANG_HOC"));
        var profile=(Map<?,?>)((List<?>)institution.list("profiles",1,20,"sv1").get("items")).getFirst();
        assertEquals("K26.PM1",profile.get("group"));
        assertEquals("Kỹ thuật phần mềm",profile.get("major"));
        assertEquals("2006-03-12",profile.get("birthDate"));
    }

    @Test
    void profilesValidateIdentityStatusAndPreserveReferencedStructure() {
        long group=group();
        assertThrows(ResponseStatusException.class,()->create("profiles",Map.of("studentId",2,"groupId",group)));
        assertThrows(ResponseStatusException.class,()->create("profiles",Map.of("studentId",3,"groupId",group,"status","INVALID")));
        assertThrows(ResponseStatusException.class,()->create("profiles",Map.of("studentId",3,"groupId",group,"birthDate",LocalDate.now().plusDays(1).toString())));
        assertThrows(ResponseStatusException.class,()->create("profiles",Map.of("studentId",3,"groupId",group,"phone","not-a-phone")));
        create("profiles",Map.of("studentId",3,"groupId",group,"status","BAO_LUU"));
        assertThrows(ResponseStatusException.class,()->institution.update("profiles",3,input(Map.of("studentId",4,"groupId",group))));
        assertThrows(org.springframework.dao.DataAccessException.class,()->institution.delete("groups",group));
        long otherMajor=create("majors",Map.of("code","HTTT","name","Hệ thống thông tin","departmentId",1));
        assertThrows(ResponseStatusException.class,()->institution.update("groups",group,input(Map.of("code","K26.PM1","name","PM","majorId",otherMajor,"cohortId",1))));
        institution.delete("profiles",3);
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM nguoi_dung",Integer.class));
    }

    @Test
    void graduationRequiresPassedMandatoryCoursesAndCreditsAndCountsRetakesOnce() {
        long group=group();
        create("profiles",Map.of("studentId",3,"groupId",group,"status","DANG_HOC"));
        long program=create("programs",Map.of("code","PM26","name","Chương trình PM 2026","majorId",1,"credits",6));
        institution.addCourse(program,new InstitutionController.CourseInput(1L,1,true));
        institution.addCourse(program,new InstitutionController.CourseInput(2L,2,true));
        assertThrows(org.springframework.dao.DataAccessException.class,()->institution.addCourse(program,new InstitutionController.CourseInput(1L,2,false)));
        assertThrows(ResponseStatusException.class,()->institution.update("profiles",3,input(Map.of("groupId",group,"status","TOT_NGHIEP"))));
        db.update("INSERT INTO lop_hoc(id,ma_lop,mon_hoc_id,giang_vien_id,hoc_ky,da_chot_diem) VALUES (1,'L1',1,2,'2026-1',1),(2,'L2',1,2,'2026-2',1),(3,'L3',2,2,'2026-2',1)");
        db.update("INSERT INTO dang_ky(lop_hoc_id,sinh_vien_id,diem_tong_ket,diem_chu) VALUES (1,3,3,'F'),(2,3,9,'A'),(3,3,7,'B')");
        var progress=institution.myProgram(student);
        assertEquals(6L,progress.get("completedCredits"));
        var ownProgram=(Map<?,?>)((List<?>)progress.get("programs")).getFirst();
        var courses=(List<Map<String,Object>>)ownProgram.get("courses");
        assertEquals(2,courses.size());
        assertTrue(courses.stream().allMatch(row->Boolean.TRUE.equals(row.get("passed"))));
        assertEquals("A",courses.getFirst().get("letter"));
        institution.update("profiles",3,input(Map.of("groupId",group,"status","TOT_NGHIEP")));
        assertEquals("TOT_NGHIEP",db.queryForObject("SELECT trang_thai FROM ho_so_sinh_vien WHERE sinh_vien_id=3",String.class));
        assertEquals(program,db.queryForObject("SELECT chuong_trinh_tot_nghiep_id FROM ho_so_sinh_vien WHERE sinh_vien_id=3",Long.class));
        assertThrows(ResponseStatusException.class,()->institution.addCourse(program,new InstitutionController.CourseInput(2L,3,false)));
        assertThrows(ResponseStatusException.class,()->institution.update("profiles",3,input(Map.of("groupId",group,"status","DANG_HOC"))));
        long newProgram=create("programs",Map.of("code","PM28","name","Chương trình PM 2028","majorId",1,"credits",6));
        institution.addCourse(newProgram,new InstitutionController.CourseInput(1L,1,true));
        assertEquals(1,institution.courses(newProgram).size());
        db.update("UPDATE lop_hoc SET da_chot_diem=0");
        institution.update("profiles",3,input(Map.of("groupId",group,"status","TOT_NGHIEP","phone","0912345678")));
        assertEquals("0912345678",db.queryForObject("SELECT dien_thoai FROM ho_so_sinh_vien WHERE sinh_vien_id=3",String.class));
        assertThrows(ResponseStatusException.class,()->institution.delete("profiles",3));
    }

    @Test
    void curriculumCourseDeletionIsScopedToItsProgramAndStudentSeesOnlyOwnProfile() {
        long group=group();
        create("profiles",Map.of("studentId",3,"groupId",group));
        long program=create("programs",Map.of("code","PM26","name","PM 2026","majorId",1,"credits",3));
        long other=create("programs",Map.of("code","PM27","name","PM 2027","majorId",1,"credits",3));
        institution.addCourse(program,new InstitutionController.CourseInput(1L,1,true));
        long course=((Number)institution.courses(program).getFirst().get("id")).longValue();
        assertThrows(ResponseStatusException.class,()->institution.removeCourse(other,course));
        assertEquals(1,institution.courses(program).size());
        var noProfile=institution.myProgram(()->"sv2");
        assertNull(noProfile.get("profile"));
        assertTrue(((List<?>)noProfile.get("programs")).isEmpty());
        assertThrows(ResponseStatusException.class,()->institution.myProgram(()->"gv1"));
        institution.removeCourse(program,course);
        assertTrue(institution.courses(program).isEmpty());
    }

    @Test
    void administrativeApiRejectsStudentAndAnonymousAccess() throws Exception {
        assertEquals(401,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/institution/catalog"))
                .andReturn().getResponse().getStatus());
        db.update("UPDATE nguoi_dung SET mat_khau=? WHERE id=3",encoder.encode("Student2026"));
        var token=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/csrf")).andReturn();
        var csrf=json.readTree(token.getResponse().getContentAsString());
        var session=(org.springframework.mock.web.MockHttpSession)token.getRequest().getSession();
        var login=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/login").session(session)
                .header(csrf.get("header").asText(),csrf.get("token").asText()).param("username","sv1").param("password","Student2026")).andReturn();
        assertEquals(204,login.getResponse().getStatus());
        var authenticated=(org.springframework.mock.web.MockHttpSession)login.getRequest().getSession();
        assertEquals(403,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/institution/catalog").session(authenticated))
                .andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/my-program").session(authenticated))
                .andReturn().getResponse().getStatus());
    }
}
