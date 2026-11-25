package vn.edu.solop;

import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
class OperationsStore implements InitializingBean {
    private final JdbcTemplate db;
    private final Clock clock;
    private final DatabaseDialect dialect;
    OperationsStore(JdbcTemplate db,Clock clock,DatabaseDialect dialect){this.db=db;this.clock=clock;this.dialect=dialect;}
    @Override public void afterPropertiesSet() {
        String id=dialect.mysql()?"BIGINT":"INTEGER";
        String suffix=dialect.mysql()?" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci":"";
        db.execute("""
                CREATE TABLE IF NOT EXISTS nhat_ky_he_thong (
                  id VARCHAR(36) PRIMARY KEY,tai_khoan VARCHAR(20) NOT NULL,phuong_thuc VARCHAR(8) NOT NULL,
                  duong_dan VARCHAR(240) NOT NULL,ma_phan_hoi INTEGER NOT NULL,thoi_gian BIGINT NOT NULL,
                  ma_yeu_cau VARCHAR(36) NOT NULL)
                """+suffix);
        db.execute("""
                CREATE TABLE IF NOT EXISTS thong_bao (
                  id VARCHAR(36) PRIMARY KEY,nguoi_dung_id %s NOT NULL,tieu_de VARCHAR(160) NOT NULL,
                  noi_dung VARCHAR(500) NOT NULL,thoi_gian BIGINT NOT NULL,da_doc INTEGER NOT NULL DEFAULT 0,
                  FOREIGN KEY(nguoi_dung_id) REFERENCES nguoi_dung(id))
                """.formatted(id)+suffix);
        index("nhat_ky_he_thong","idx_nhat_ky_thoi_gian","thoi_gian");
        index("thong_bao","idx_thong_bao_nguoi_dung","nguoi_dung_id,da_doc,thoi_gian");
    }
    private void index(String table,String name,String columns) {
        if(!dialect.mysql()){db.execute("CREATE INDEX IF NOT EXISTS "+name+" ON "+table+"("+columns+")");return;}
        if(db.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?",Integer.class,table,name)==0)
            db.execute("CREATE INDEX "+name+" ON "+table+"("+columns+")");
    }
    void audit(String actor,String method,String path,int status,String requestId) {
        db.update("INSERT INTO nhat_ky_he_thong(id,tai_khoan,phuong_thuc,duong_dan,ma_phan_hoi,thoi_gian,ma_yeu_cau) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),actor,method,path.substring(0,Math.min(240,path.length())),status,clock.millis(),requestId);
    }
    void notifyUser(long user,String title,String message) {
        db.update("INSERT INTO thong_bao(id,nguoi_dung_id,tieu_de,noi_dung,thoi_gian) VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(),user,title,message,clock.millis());
    }
    void notifyClass(long classId,String title,String message) {
        for(Long student:db.queryForList("SELECT sinh_vien_id FROM dang_ky WHERE lop_hoc_id=?",Long.class,classId))
            notifyUser(student,title,message);
    }
    void notifyTeacher(long sessionId,String title,String message) {
        Long teacher=db.queryForObject("SELECT l.giang_vien_id FROM buoi_hoc b JOIN lop_hoc l ON l.id=b.lop_hoc_id WHERE b.id=?",Long.class,sessionId);
        notifyUser(teacher,title,message);
    }
}
