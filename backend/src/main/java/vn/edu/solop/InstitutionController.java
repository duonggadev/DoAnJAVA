package vn.edu.solop;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static vn.edu.solop.AdminController.*;

@RestController
@RequestMapping("/api")
class InstitutionController {
    private final JdbcTemplate db;
    private final Clock clock;
    private static final Map<String,String> TABLES=Map.of("departments","khoa","majors","nganh","cohorts","khoa_hoc",
            "groups","lop_hanh_chinh","programs","chuong_trinh","profiles","ho_so_sinh_vien");

    InstitutionController(JdbcTemplate db,Clock clock) { this.db=db; this.clock=clock; }

    @GetMapping("/admin/institution/catalog")
    Map<String,Object> catalog() {
        var result=new LinkedHashMap<String,Object>();
        for(String entity:List.of("departments","majors","cohorts","groups","programs"))
            result.put(entity,db.queryForList("SELECT * FROM ("+select(entity)+") entries ORDER BY code,id"));
        result.put("students",db.queryForList("SELECT id,ma_so AS code,ho_ten AS name FROM nguoi_dung WHERE vai_tro='SINH_VIEN' ORDER BY ma_so"));
        return result;
    }

    @GetMapping("/admin/institution/{entity}")
    Map<String,Object> list(@PathVariable String entity,@RequestParam(defaultValue="1") int page,
                            @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String q) {
        table(entity);
        if(page<1 || page>1_000_000 || size<1 || size>100 || q.length()>100)throw bad("Trang từ 1, tối đa 100 dòng và từ khóa tối đa 100 ký tự.");
        String search="%"+q.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String filtered=" FROM ("+select(entity)+") entries WHERE LOWER(code) LIKE ? ESCAPE '!' OR LOWER(name) LIKE ? ESCAPE '!'";
        long count=db.queryForObject("SELECT COUNT(*)"+filtered,Long.class,search,search);
        var rows=db.queryForList("SELECT *"+filtered+" ORDER BY code,id LIMIT ? OFFSET ?",search,search,size,(page-1)*size);
        return Map.of("items",rows,"total",count,"page",page,"size",size);
    }

    @PostMapping("/admin/institution/{entity}")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Map<String,Object> create(@PathVariable String entity,@RequestBody Input input) {
        return Map.of("id",save(entity,null,input));
    }

    @PutMapping("/admin/institution/{entity}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void update(@PathVariable String entity,@PathVariable long id,@RequestBody Input input) { save(entity,id,input); }

    private long save(String entity,Long id,Input input) {
        String table=table(entity);
        if("profiles".equals(entity))return saveProfile(id,input);
        String code=identifier(input.code());
        String name=required(input.name(),150,"Tên");
        var columns=new ArrayList<>(List.of("ma","ten"));
        var values=new ArrayList<Object>(List.of(code,name));
        if(id!=null)updated(db.update("UPDATE "+table+" SET ma=ma WHERE id=?",id));
        switch(entity) {
            case "majors" -> { requireExisting("khoa",input.departmentId()); columns.add("khoa_id");values.add(input.departmentId()); }
            case "cohorts" -> {
                if(input.startYear()==null || input.endYear()==null || input.startYear()<1900 || input.endYear()>2200 || input.endYear()<input.startYear())
                    throw bad("Năm khóa học cần từ 1900 đến 2200 và năm kết thúc không trước năm bắt đầu.");
                columns.addAll(List.of("nam_bat_dau","nam_ket_thuc"));values.addAll(List.of(input.startYear(),input.endYear()));
            }
            case "groups" -> {
                requireExisting("nganh",input.majorId());requireExisting("khoa_hoc",input.cohortId());
                if(id!=null && db.queryForObject("SELECT COUNT(*) FROM ho_so_sinh_vien WHERE lop_hanh_chinh_id=?",Integer.class,id)>0) {
                    var previous=db.queryForMap("SELECT nganh_id,khoa_hoc_id FROM lop_hanh_chinh WHERE id=?",id);
                    if(number(previous,"nganh_id")!=input.majorId() || number(previous,"khoa_hoc_id")!=input.cohortId())
                        throw conflict("Lớp đã có hồ sơ sinh viên; không đổi ngành hoặc khóa học của lớp.");
                }
                columns.addAll(List.of("nganh_id","khoa_hoc_id"));values.addAll(List.of(input.majorId(),input.cohortId()));
            }
            case "programs" -> {
                requireExisting("nganh",input.majorId());
                if(input.credits()==null || input.credits()<1 || input.credits()>500)throw bad("Tổng tín chỉ chương trình từ 1 đến 500.");
                if(id!=null)ensureProgramEditable(id);
                columns.addAll(List.of("nganh_id","so_tin_chi"));values.addAll(List.of(input.majorId(),input.credits()));
            }
            default -> { }
        }
        if(id==null) {
            db.update("INSERT INTO "+table+" ("+String.join(",",columns)+") VALUES ("+String.join(",",java.util.Collections.nCopies(values.size(),"?"))+")",values.toArray());
            return db.queryForObject("SELECT id FROM "+table+" WHERE ma=?",Long.class,code);
        }
        values.add(id);
        updated(db.update("UPDATE "+table+" SET "+String.join(",",columns.stream().map(column->column+"=?").toList())+" WHERE id=?",values.toArray()));
        return id;
    }

    private long saveProfile(Long id,Input input) {
        Long student=id==null?input.studentId():id;
        if(student==null || (input.studentId()!=null && !student.equals(input.studentId())))throw bad("Mã hồ sơ sinh viên chưa hợp lệ.");
        if(db.update("UPDATE nguoi_dung SET ban_phien=ban_phien WHERE id=? AND vai_tro='SINH_VIEN'",student)==0)
            throw bad("Hồ sơ phải gắn với tài khoản sinh viên.");
        requireExisting("lop_hanh_chinh",input.groupId());
        updated(db.update("UPDATE lop_hanh_chinh SET ma=ma WHERE id=?",input.groupId()));
        String status=input.status()==null?"DANG_HOC":input.status();
        if(!Set.of("DANG_HOC","BAO_LUU","THOI_HOC","TOT_NGHIEP").contains(status))throw bad("Trạng thái sinh viên chưa hợp lệ.");
        String gender=input.gender()==null || input.gender().isBlank()?null:input.gender();
        if(gender!=null && !Set.of("NAM","NU","KHAC").contains(gender))throw bad("Giới tính chưa hợp lệ.");
        if(input.birthDate()!=null && (input.birthDate().getYear()<1900 || input.birthDate().isAfter(LocalDate.now(clock))))throw bad("Ngày sinh chưa hợp lệ.");
        String phone=input.phone()==null || input.phone().isBlank()?null:required(input.phone(),25,"Số điện thoại");
        if(phone!=null && !phone.matches("[+0-9().\\s-]{6,25}"))throw bad("Số điện thoại chưa hợp lệ.");
        Long graduationProgram=null;
        boolean graduated=false;
        if(id!=null) {
            var old=db.queryForList("SELECT g.nganh_id,h.lop_hanh_chinh_id,h.trang_thai,h.chuong_trinh_tot_nghiep_id FROM ho_so_sinh_vien h JOIN lop_hanh_chinh g ON g.id=h.lop_hanh_chinh_id WHERE h.sinh_vien_id=?",student);
            if(old.isEmpty())throw missing();
            var previous=old.getFirst();
            graduated="TOT_NGHIEP".equals(previous.get("trang_thai"));
            if(graduated) {
                if(!"TOT_NGHIEP".equals(status) || number(previous,"lop_hanh_chinh_id")!=input.groupId())
                    throw conflict("Hồ sơ đã tốt nghiệp; chỉ cập nhật thông tin cá nhân, không đổi lớp hoặc trạng thái.");
                graduationProgram=previous.get("chuong_trinh_tot_nghiep_id")==null?null:number(previous,"chuong_trinh_tot_nghiep_id");
            }
            long major=db.queryForObject("SELECT nganh_id FROM lop_hanh_chinh WHERE id=?",Long.class,input.groupId());
            if(number(old.getFirst(),"nganh_id")!=major && db.queryForObject("SELECT COUNT(*) FROM dang_ky WHERE sinh_vien_id=?",Integer.class,student)>0)
                throw conflict("Sinh viên đã có hồ sơ học tập; chuyển ngành cần xử lý riêng để bảo toàn kết quả.");
        }
        if("TOT_NGHIEP".equals(status) && !graduated)graduationProgram=ensureGraduation(student,input.groupId());
        String birth=input.birthDate()==null?null:input.birthDate().toString();
        if(id==null)db.update("INSERT INTO ho_so_sinh_vien(sinh_vien_id,lop_hanh_chinh_id,ngay_sinh,gioi_tinh,dien_thoai,trang_thai,chuong_trinh_tot_nghiep_id) VALUES (?,?,?,?,?,?,?)",
                student,input.groupId(),birth,gender,phone,status,graduationProgram);
        else updated(db.update("UPDATE ho_so_sinh_vien SET lop_hanh_chinh_id=?,ngay_sinh=?,gioi_tinh=?,dien_thoai=?,trang_thai=?,chuong_trinh_tot_nghiep_id=? WHERE sinh_vien_id=?",
                input.groupId(),birth,gender,phone,status,graduationProgram,student));
        return student;
    }

    @DeleteMapping("/admin/institution/{entity}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@PathVariable String entity,@PathVariable long id) {
        String table=table(entity);
        if("profiles".equals(entity)) {
            updated(db.update("UPDATE nguoi_dung SET ban_phien=ban_phien WHERE id=? AND vai_tro='SINH_VIEN'",id));
            if(db.queryForObject("SELECT COUNT(*) FROM dang_ky WHERE sinh_vien_id=?",Integer.class,id)>0)
                throw conflict("Sinh viên đã có đăng ký học phần; giữ hồ sơ và cập nhật trạng thái thay vì xóa.");
            updated(db.update("DELETE FROM ho_so_sinh_vien WHERE sinh_vien_id=?",id));
        } else {
            if("programs".equals(entity))ensureProgramEditable(id);
            updated(db.update("DELETE FROM "+table+" WHERE id=?",id));
        }
    }

    @GetMapping("/admin/institution/programs/{id}/courses")
    List<Map<String,Object>> courses(@PathVariable long id) {
        requireExisting("chuong_trinh",id);
        return db.queryForList("""
                SELECT c.id,c.mon_hoc_id AS `subjectId`,m.ma_mon AS code,m.ten_mon AS subject,m.so_tin_chi AS credits,
                    c.hoc_ky_du_kien AS semester,c.bat_buoc AS `required`
                FROM chuong_trinh_mon c JOIN mon_hoc m ON m.id=c.mon_hoc_id WHERE c.chuong_trinh_id=? ORDER BY c.hoc_ky_du_kien,m.ma_mon
                """,id);
    }

    @PostMapping("/admin/institution/programs/{id}/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    void addCourse(@PathVariable long id,@RequestBody CourseInput input) {
        requireExisting("chuong_trinh",id);ensureProgramEditable(id);requireExisting("mon_hoc",input.subjectId());
        if(input.semester()==null || input.semester()<1 || input.semester()>20 || input.required()==null)throw bad("Chọn học kỳ dự kiến 1–20 và loại học phần.");
        db.update("INSERT INTO chuong_trinh_mon(chuong_trinh_id,mon_hoc_id,hoc_ky_du_kien,bat_buoc) VALUES (?,?,?,?)",
                id,input.subjectId(),input.semester(),input.required()?1:0);
    }

    @DeleteMapping("/admin/institution/programs/{id}/courses/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void removeCourse(@PathVariable long id,@PathVariable long courseId) {
        requireExisting("chuong_trinh",id);ensureProgramEditable(id);
        updated(db.update("DELETE FROM chuong_trinh_mon WHERE id=? AND chuong_trinh_id=?",courseId,id));
    }

    @GetMapping("/my-program")
    Map<String,Object> myProgram(Principal actor) {
        var users=db.queryForList("SELECT id FROM nguoi_dung WHERE ma_so=? AND vai_tro='SINH_VIEN'",Long.class,actor.getName());
        if(users.isEmpty())throw missing();
        long student=users.getFirst();
        var profiles=db.queryForList(select("profiles")+" WHERE u.id=?",student);
        var result=new LinkedHashMap<String,Object>();
        result.put("profile",profiles.isEmpty()?null:profiles.getFirst());
        var programs=new ArrayList<Map<String,Object>>();
        if(!profiles.isEmpty()) {
            long major=((Number)profiles.getFirst().get("majorId")).longValue();
            for(var program:db.queryForList(select("programs")+" WHERE p.nganh_id=? ORDER BY p.ma",major)) {
                long id=number(program,"id");
                var items=progress(id,student);
                program.put("courses",items);
                program.put("completedCredits",items.stream().filter(row->Boolean.TRUE.equals(row.get("passed"))).mapToLong(row->number(row,"credits")).sum());
                programs.add(program);
            }
        }
        result.put("programs",programs);
        Long completed=db.queryForObject("""
                SELECT COALESCE(SUM(p.credits),0) FROM (
                  SELECT m.id,m.so_tin_chi AS credits FROM dang_ky d JOIN lop_hoc l ON l.id=d.lop_hoc_id JOIN mon_hoc m ON m.id=l.mon_hoc_id
                  WHERE d.sinh_vien_id=? AND l.da_chot_diem=1 AND d.diem_tong_ket>=4 GROUP BY m.id,m.so_tin_chi
                ) p
                """,Long.class,student);
        result.put("completedCredits",completed);
        return result;
    }

    private List<Map<String,Object>> progress(long program,long student) {
        var rows=db.queryForList("""
                SELECT c.id,c.mon_hoc_id AS `subjectId`,m.ma_mon AS code,m.ten_mon AS subject,m.so_tin_chi AS credits,
                    c.hoc_ky_du_kien AS semester,c.bat_buoc AS `required`,p.total
                FROM chuong_trinh_mon c JOIN mon_hoc m ON m.id=c.mon_hoc_id LEFT JOIN (
                    SELECT l.mon_hoc_id,MAX(d.diem_tong_ket) AS total FROM dang_ky d JOIN lop_hoc l ON l.id=d.lop_hoc_id
                    WHERE d.sinh_vien_id=? AND l.da_chot_diem=1 GROUP BY l.mon_hoc_id
                ) p ON p.mon_hoc_id=m.id WHERE c.chuong_trinh_id=? ORDER BY c.hoc_ky_du_kien,m.ma_mon
                """,student,program);
        for(var row:rows) {
            var score=(Number)row.get("total");
            row.put("passed",score!=null && score.doubleValue()>=4);
            row.put("letter",score==null?null:AcademicController.gradeLetter(score.doubleValue()));
        }
        return rows;
    }

    private long ensureGraduation(long student,long group) {
        long major=db.queryForObject("SELECT nganh_id FROM lop_hanh_chinh WHERE id=?",Long.class,group);
        var programs=db.queryForList("SELECT id,so_tin_chi FROM chuong_trinh WHERE nganh_id=? ORDER BY id",major);
        for(var program:programs) {
            updated(db.update("UPDATE chuong_trinh SET ma=ma WHERE id=?",number(program,"id")));
            var rows=progress(number(program,"id"),student);
            long completed=rows.stream().filter(row->Boolean.TRUE.equals(row.get("passed"))).mapToLong(row->number(row,"credits")).sum();
            boolean mandatory=rows.stream().filter(row->number(row,"required")==1).allMatch(row->Boolean.TRUE.equals(row.get("passed")));
            long requiredCredits=db.queryForObject("SELECT so_tin_chi FROM chuong_trinh WHERE id=?",Long.class,number(program,"id"));
            if(!rows.isEmpty() && mandatory && completed>=requiredCredits)return number(program,"id");
        }
        throw conflict("Chưa đủ điều kiện tốt nghiệp: cần hoàn thành học phần bắt buộc và đủ tín chỉ của một chương trình thuộc ngành.");
    }

    private void ensureProgramEditable(long id) {
        updated(db.update("UPDATE chuong_trinh SET ma=ma WHERE id=?",id));
        if(db.queryForObject("""
                SELECT COUNT(*) FROM ho_so_sinh_vien h WHERE h.chuong_trinh_tot_nghiep_id=? AND h.trang_thai='TOT_NGHIEP'
                """,Integer.class,id)>0)throw conflict("Chương trình đã có sinh viên tốt nghiệp; tạo phiên bản mới để bảo toàn hồ sơ.");
    }

    private static String select(String entity) {
        return switch(entity) {
            case "departments" -> "SELECT id,ma AS code,ten AS name FROM khoa";
            case "majors" -> "SELECT n.id,n.ma AS code,n.ten AS name,n.khoa_id AS `departmentId`,k.ten AS department FROM nganh n JOIN khoa k ON k.id=n.khoa_id";
            case "cohorts" -> "SELECT id,ma AS code,ten AS name,nam_bat_dau AS `startYear`,nam_ket_thuc AS `endYear` FROM khoa_hoc";
            case "groups" -> "SELECT g.id,g.ma AS code,g.ten AS name,g.nganh_id AS `majorId`,g.khoa_hoc_id AS `cohortId`,n.ten AS major,k.ten AS cohort FROM lop_hanh_chinh g JOIN nganh n ON n.id=g.nganh_id JOIN khoa_hoc k ON k.id=g.khoa_hoc_id";
            case "programs" -> "SELECT p.id,p.ma AS code,p.ten AS name,p.nganh_id AS `majorId`,p.so_tin_chi AS credits,n.ten AS major FROM chuong_trinh p JOIN nganh n ON n.id=p.nganh_id";
            case "profiles" -> """
                    SELECT u.id,u.ma_so AS code,u.ho_ten AS name,h.lop_hanh_chinh_id AS `groupId`,g.ma AS `group`,
                        g.nganh_id AS `majorId`,n.ten AS major,k.ten AS cohort,h.ngay_sinh AS `birthDate`,h.gioi_tinh AS gender,
                        h.dien_thoai AS phone,h.trang_thai AS status,h.chuong_trinh_tot_nghiep_id AS `graduationProgramId` FROM ho_so_sinh_vien h JOIN nguoi_dung u ON u.id=h.sinh_vien_id
                        JOIN lop_hanh_chinh g ON g.id=h.lop_hanh_chinh_id JOIN nganh n ON n.id=g.nganh_id JOIN khoa_hoc k ON k.id=g.khoa_hoc_id
                    """;
            default -> throw missing();
        };
    }

    private static String table(String entity) { String table=TABLES.get(entity);if(table==null)throw missing();return table; }
    private void requireExisting(String table,Long id) {
        if(id==null || id<1 || db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=?",Integer.class,id)==0)throw bad("Danh mục được chọn không tồn tại.");
    }
    private static String identifier(String raw) {
        String code=required(raw,20,"Mã");if(!code.matches("[A-Za-z0-9._-]+"))throw bad("Mã chỉ dùng chữ không dấu, số, dấu chấm và gạch.");return code;
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    record Input(String code,String name,Long departmentId,Long majorId,Long cohortId,Long studentId,Long groupId,
                 LocalDate birthDate,String gender,String phone,String status,Integer startYear,Integer endYear,Integer credits) { }
    record CourseInput(Long subjectId,Integer semester,Boolean required) { }
}
