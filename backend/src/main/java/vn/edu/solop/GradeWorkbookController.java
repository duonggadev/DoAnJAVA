package vn.edu.solop;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.Principal;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static vn.edu.solop.AdminController.*;

@RestController
@RequestMapping("/api/classes")
class GradeWorkbookController {
    private final AttendanceController access;
    private final LearningController learning;
    private final JdbcTemplate db;

    GradeWorkbookController(AttendanceController access, LearningController learning, JdbcTemplate db) {
        this.access=access; this.learning=learning; this.db=db;
    }

    @GetMapping("/{id}/grades.xlsx")
    ResponseEntity<byte[]> workbook(@PathVariable long id, Principal actor) throws java.io.IOException {
        var user=access.me(actor);
        if ("SINH_VIEN".equals(user.role())) throw missing();
        access.requireClass(id,user);
        try (var book=new XSSFWorkbook(); var bytes=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("Diem");
            var header=sheet.createRow(0);
            String[] titles={"Ma SV","Ho ten","Qua trinh","Thi","Du thi"};
            for(int i=0;i<titles.length;i++) header.createCell(i).setCellValue(titles[i]);
            int index=1;
            for(var item:learning.grades(id,actor)) {
                var row=sheet.createRow(index++);
                row.createCell(0).setCellValue(item.get("code").toString());
                row.createCell(1).setCellValue(item.get("name").toString());
                if(item.get("coursework")!=null) row.createCell(2).setCellValue(((Number)item.get("coursework")).doubleValue());
                row.createCell(4).setCellValue(((Number)item.get("eligible")).intValue()==1?"Du dieu kien":"Chua du chuyen can");
                if(item.get("exam")!=null) row.createCell(3).setCellValue(((Number)item.get("exam")).doubleValue());
            }
            for(int i=0;i<5;i++) sheet.autoSizeColumn(i);
            book.write(bytes);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=grades-"+id+".xlsx")
                .cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes.toByteArray());
        }
    }

    @PostMapping("/{id}/grades-import")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void upload(@PathVariable long id,@RequestParam MultipartFile file,Principal actor) {
        if(file.isEmpty() || file.getSize()>2_000_000) throw bad("Tệp Excel phải nhỏ hơn 2 MB.");
        updated(db.update("UPDATE lop_hoc SET ma_lop=ma_lop WHERE id=? AND giang_vien_id=(SELECT id FROM nguoi_dung WHERE ma_so=?)",id,actor.getName()));
        try(var book=new XSSFWorkbook(file.getInputStream())) {
            if(book.getNumberOfSheets()==0)throw bad("Tệp chưa có trang tính.");
            var sheet=book.getSheetAt(0);
            var format=new DataFormatter();
            var header=sheet.getRow(0);
            String[] expected={"Ma SV","Ho ten","Qua trinh","Thi"};
            for(int column=0;column<expected.length;column++) {
                if(header==null || !expected[column].equals(format.formatCellValue(header.getCell(column)).strip()))
                    throw bad("Sai cấu trúc tệp. Hãy tải mẫu Excel của lớp rồi nhập điểm.");
            }
            if(sheet.getLastRowNum()>1000) throw bad("Tệp có quá 1000 dòng.");
            var seen=new java.util.HashSet<String>();
            for(int i=1;i<=sheet.getLastRowNum();i++) {
                var row=sheet.getRow(i);
                if(row==null) continue;
                String code=format.formatCellValue(row.getCell(0)).strip();
                if(code.isEmpty()) {
                    if(!format.formatCellValue(row.getCell(2)).isBlank() || !format.formatCellValue(row.getCell(3)).isBlank())
                        throw bad("Thiếu mã sinh viên ở dòng "+(i+1));
                    continue;
                }
                if(!seen.add(code)) throw bad("Trùng mã sinh viên ở dòng "+(i+1));
                var ids=db.queryForList("""
                    SELECT d.id FROM dang_ky d JOIN nguoi_dung s ON s.id=d.sinh_vien_id
                    WHERE d.lop_hoc_id=? AND s.ma_so=?
                    """,Long.class,id,code);
                if(ids.isEmpty()) throw bad("Sinh viên chưa đăng ký lớp ở dòng "+(i+1));
                learning.editGrades(ids.getFirst(),new LearningController.Grades(score(row.getCell(2)),score(row.getCell(3))),actor);
            }
            if(seen.isEmpty()) throw bad("Tệp chưa có dòng điểm.");
        } catch(java.io.IOException | org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException error) {
            throw bad("Không đọc được tệp .xlsx.");
        } catch(org.apache.poi.ooxml.POIXMLException error) {
            throw bad("Tệp không có cấu trúc Excel .xlsx hợp lệ.");
        } catch(NumberFormatException error) { throw bad("Điểm cần là số từ 0 đến 10."); }
    }

    private BigDecimal score(Cell cell) {
        if(cell==null || cell.getCellType()==CellType.BLANK) return null;
        if(cell.getCellType()!=CellType.NUMERIC && cell.getCellType()!=CellType.STRING)
            throw bad("Điểm cần là số trực tiếp từ 0 đến 10; không nhận công thức, TRUE/FALSE hoặc lỗi ô.");
        return new BigDecimal(cell.getCellType()==CellType.NUMERIC?Double.toString(cell.getNumericCellValue()):cell.getStringCellValue().strip());
    }
}