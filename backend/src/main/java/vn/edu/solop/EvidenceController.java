package vn.edu.solop;

import java.security.Principal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static vn.edu.solop.AdminController.*;

@RestController
@RequestMapping("/api")
class EvidenceController {
    private final JdbcTemplate db;
    private final AcademicController academic;
    EvidenceController(JdbcTemplate db,AcademicController academic){this.db=db;this.academic=academic;}

    @PostMapping("/sessions/{id}/leave-file")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    void upload(@PathVariable long id,@RequestParam String reason,@RequestParam MultipartFile file,Principal actor) throws java.io.IOException {
        if(file.isEmpty() || file.getSize()>2_000_000)throw bad("Minh chứng cần có dung lượng từ 1 byte đến 2 MB.");
        byte[] bytes=file.getBytes();
        String mime=detect(bytes);
        academic.requestLeave(id,new AcademicController.LeaveInput(reason,null),actor);
        long leave=db.queryForObject("""
                SELECT x.id FROM don_xin_phep x JOIN nguoi_dung u ON u.id=x.sinh_vien_id WHERE x.buoi_hoc_id=? AND u.ma_so=?
                """,Long.class,id,actor.getName());
        String evidence=UUID.randomUUID().toString();
        db.update("INSERT INTO minh_chung(id,don_id,loai,noi_dung) VALUES (?,?,?,?)",evidence,leave,mime,bytes);
        db.update("UPDATE don_xin_phep SET minh_chung=? WHERE id=?","/api/leave-evidence/"+evidence,leave);
    }

    @GetMapping("/leave-evidence/{id}")
    ResponseEntity<byte[]> download(@PathVariable String id,Principal actor) {
        var rows=db.queryForList("""
                SELECT f.loai,f.noi_dung FROM minh_chung f JOIN don_xin_phep x ON x.id=f.don_id
                JOIN buoi_hoc b ON b.id=x.buoi_hoc_id JOIN lop_hoc l ON l.id=b.lop_hoc_id
                JOIN nguoi_dung u ON u.ma_so=?
                WHERE f.id=? AND (u.vai_tro='ADMIN' OR u.id=x.sinh_vien_id OR u.id=l.giang_vien_id)
                """,actor.getName(),id);
        if(rows.isEmpty())throw missing();
        var row=rows.getFirst();
        String mime=row.get("loai").toString();
        String extension=mime.equals("application/pdf")?".pdf":mime.equals("image/png")?".png":".jpg";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=minh-chung"+extension)
                .body((byte[])row.get("noi_dung"));
    }

    private String detect(byte[] bytes) {
        if(bytes.length>=5 && new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))return "application/pdf";
        if(bytes.length>=8 && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,8),
                new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
        if(bytes.length>=3 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255)return "image/jpeg";
        throw bad("Chỉ nhận minh chứng PDF, PNG hoặc JPEG.");
    }
}