package vn.edu.solop;

import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
class EnrollmentSchema implements InitializingBean {
    private final JdbcTemplate db;

    EnrollmentSchema(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void afterPropertiesSet() {
        try(var connection=db.getDataSource().getConnection()) {
            if(connection.getMetaData().getDatabaseProductName().matches(".*(MySQL|MariaDB).*"))return;
        } catch(java.sql.SQLException error){throw new IllegalStateException("Cannot inspect database",error);}
        addColumns("nguoi_dung", Map.of("bi_khoa", "INTEGER NOT NULL DEFAULT 0",
                "ban_phien", "INTEGER NOT NULL DEFAULT 0"));
        addColumns("mon_hoc", Map.of("mon_tien_quyet_id", "INTEGER REFERENCES mon_hoc(id)",
                "ty_le_chuyen_can", "INTEGER NOT NULL DEFAULT 10", "ty_le_qua_trinh", "INTEGER NOT NULL DEFAULT 30",
                "ty_le_thi", "INTEGER NOT NULL DEFAULT 60"));
        addColumns("lop_hoc", Map.of("si_so_toi_da", "INTEGER NOT NULL DEFAULT 60",
                "da_chot_diem", "INTEGER NOT NULL DEFAULT 0"));
        addColumns("dang_ky", Map.of("ngay_dang_ky", "TEXT", "diem_tong_ket", "DECIMAL(4,2)", "diem_chu", "TEXT", "bi_cam_thi", "INTEGER NOT NULL DEFAULT 0"));
        addColumns("buoi_hoc", Map.of("noi_dung", "TEXT", "qr_secret", "TEXT"));
        addColumns("diem_danh", Map.of("device_hash", "TEXT"));
        db.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_diem_danh_device ON diem_danh(buoi_hoc_id, device_hash)");
    }

    private void addColumns(String table, Map<String, String> definitions) {
        // Table names and definitions are internal constants.
        var existing = db.query("SELECT * FROM " + table + " WHERE 1=0", rows -> {
            var names = new java.util.HashSet<String>();
            var metadata = rows.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) names.add(metadata.getColumnName(i));
            return names;
        });
        definitions.forEach((column, definition) -> {
            if (!existing.contains(column)) db.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        });
    }
}
