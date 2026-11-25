package vn.edu.solop;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static vn.edu.solop.AdminController.*;

@RestController
@RequestMapping("/api")
class OperationsController {
    private final JdbcTemplate db;
    OperationsController(JdbcTemplate db){this.db=db;}
    @GetMapping("/admin/audit")
    Map<String,Object> audit(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,
                             @RequestParam(defaultValue="")String q) {
        validate(page,size);
        if(q.length()>100)throw bad("Từ khóa tối đa 100 ký tự.");
        String search="%"+q.strip().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" WHERE LOWER(tai_khoan) LIKE ? ESCAPE '!' OR LOWER(duong_dan) LIKE ? ESCAPE '!'";
        var rows=db.queryForList("""
                SELECT id,tai_khoan AS actor,phuong_thuc AS method,duong_dan AS path,ma_phan_hoi AS status,
                  thoi_gian AS time,ma_yeu_cau AS `requestId` FROM nhat_ky_he_thong
                """+where+" ORDER BY thoi_gian DESC,id DESC LIMIT ? OFFSET ?",search,search,size,(page-1)*size);
        return Map.of("items",rows,"total",db.queryForObject("SELECT COUNT(*) FROM nhat_ky_he_thong"+where,Long.class,search,search),"page",page,"size",size);
    }
    @GetMapping("/notifications")
    Map<String,Object> notifications(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int size,Principal actor) {
        validate(page,size);
        Long user=db.queryForObject("SELECT id FROM nguoi_dung WHERE ma_so=?",Long.class,actor.getName());
        var rows=db.queryForList("""
                SELECT id,tieu_de AS title,noi_dung AS message,thoi_gian AS time,da_doc AS `read`
                FROM thong_bao WHERE nguoi_dung_id=? ORDER BY thoi_gian DESC,id DESC LIMIT ? OFFSET ?
                """,user,size,(page-1)*size);
        return Map.of("items",rows,"total",db.queryForObject("SELECT COUNT(*) FROM thong_bao WHERE nguoi_dung_id=?",Long.class,user),
                "unread",db.queryForObject("SELECT COUNT(*) FROM thong_bao WHERE nguoi_dung_id=? AND da_doc=0",Long.class,user),"page",page,"size",size);
    }
    @PutMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void read(@PathVariable String id,Principal actor) {
        updated(db.update("UPDATE thong_bao SET da_doc=1 WHERE id=? AND nguoi_dung_id=(SELECT id FROM nguoi_dung WHERE ma_so=?)",id,actor.getName()));
    }
    @PutMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void readAll(Principal actor) {
        db.update("UPDATE thong_bao SET da_doc=1 WHERE nguoi_dung_id=(SELECT id FROM nguoi_dung WHERE ma_so=?)",actor.getName());
    }
    private static void validate(int page,int size){if(page<1 || page>1_000_000 || size<1 || size>100)throw bad("Trang từ 1 và tối đa 100 dòng.");}
}
