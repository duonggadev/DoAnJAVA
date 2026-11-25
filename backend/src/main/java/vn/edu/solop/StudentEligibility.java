package vn.edu.solop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class StudentEligibility {
    private final JdbcTemplate db;
    StudentEligibility(JdbcTemplate db){this.db=db;}
    void requireActive(String code) {
        var statuses=db.queryForList("""
                SELECT h.trang_thai FROM ho_so_sinh_vien h JOIN nguoi_dung u ON u.id=h.sinh_vien_id WHERE u.ma_so=?
                """,String.class,code);
        if(!statuses.isEmpty() && !"DANG_HOC".equals(statuses.getFirst()))
            throw AdminController.conflict("Hồ sơ đang bảo lưu, thôi học hoặc đã tốt nghiệp; chưa được đăng ký và điểm danh.");
    }
}
