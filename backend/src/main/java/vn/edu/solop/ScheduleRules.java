package vn.edu.solop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
class ScheduleRules implements org.springframework.beans.factory.InitializingBean {
    private final JdbcTemplate db;
    private final DatabaseDialect dialect;

    ScheduleRules(JdbcTemplate db,DatabaseDialect dialect) {
        this.db=db;
        this.dialect=dialect;
    }

    @Override public void afterPropertiesSet() {
        db.execute("CREATE TABLE IF NOT EXISTS khoa_nghiep_vu(id INTEGER PRIMARY KEY,phien INTEGER NOT NULL DEFAULT 0)"+(dialect.mysql()?" ENGINE=InnoDB":""));
        db.update(dialect.choose("INSERT INTO khoa_nghiep_vu(id) VALUES(1) ON CONFLICT DO NOTHING","INSERT INTO khoa_nghiep_vu(id) VALUES(1) ON DUPLICATE KEY UPDATE id=id"));
    }

    // Take before account/class locks; held until the surrounding transaction commits.
    void lock() { db.update("UPDATE khoa_nghiep_vu SET phien=phien WHERE id=1"); }

    void requireAvailable(long classId, long excludedSession, String date, String start, String end, String room) {
        int conflicts=db.queryForObject("""
                SELECT COUNT(*) FROM buoi_hoc b JOIN lop_hoc other ON other.id=b.lop_hoc_id
                JOIN lop_hoc target ON target.id=?
                WHERE b.id<>? AND b.ngay_hoc=?
                  AND substr(concat(b.gio_bat_dau,':00'),1,8)<substr(concat(?,':00'),1,8) AND substr(concat(b.gio_ket_thuc,':00'),1,8)>substr(concat(?,':00'),1,8)
                  AND (other.id=target.id OR other.giang_vien_id=target.giang_vien_id
                    OR lower(trim(b.phong))=lower(trim(?))
                    OR EXISTS(SELECT 1 FROM dang_ky enrolled JOIN dang_ky shared
                        ON enrolled.sinh_vien_id=shared.sinh_vien_id
                        WHERE enrolled.lop_hoc_id=target.id AND shared.lop_hoc_id=other.id))
                """,Integer.class,classId,excludedSession,date,end,start,room);
        if(conflicts>0) throw AdminController.conflict(
                "Buổi học trùng giờ với lớp, phòng, giảng viên hoặc lịch của sinh viên đã đăng ký vào ngày "+date+".");
    }
}
