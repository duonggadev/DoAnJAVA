package vn.edu.solop;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Additive institutional data; existing learning records are never rewritten. */
@Component
@DependsOnDatabaseInitialization
class InstitutionSchema implements InitializingBean {
    private final JdbcTemplate db;

    InstitutionSchema(JdbcTemplate db) { this.db=db; }

    @Override
    public void afterPropertiesSet() {
        boolean mysql=Boolean.TRUE.equals(db.execute((ConnectionCallback<Boolean>) connection ->
                connection.getMetaData().getDatabaseProductName().matches("(?i).*mysql.*|.*mariadb.*")));
        String id=mysql?"BIGINT AUTO_INCREMENT PRIMARY KEY":"INTEGER PRIMARY KEY";
        String reference=mysql?"BIGINT":"INTEGER";
        String suffix=mysql?" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci":"";
        db.execute("CREATE TABLE IF NOT EXISTS khoa (id "+id+",ma VARCHAR(20) NOT NULL UNIQUE,ten VARCHAR(150) NOT NULL)"+suffix);
        db.execute("CREATE TABLE IF NOT EXISTS nganh (id "+id+",ma VARCHAR(20) NOT NULL UNIQUE,ten VARCHAR(150) NOT NULL,khoa_id "+reference+
                " NOT NULL,FOREIGN KEY(khoa_id) REFERENCES khoa(id))"+suffix);
        db.execute("CREATE TABLE IF NOT EXISTS khoa_hoc (id "+id+",ma VARCHAR(20) NOT NULL UNIQUE,ten VARCHAR(150) NOT NULL,"+
                "nam_bat_dau INTEGER NOT NULL,nam_ket_thuc INTEGER NOT NULL,CHECK(nam_bat_dau BETWEEN 1900 AND 2200),"+
                "CHECK(nam_ket_thuc>=nam_bat_dau AND nam_ket_thuc<=2200))"+suffix);
        db.execute("CREATE TABLE IF NOT EXISTS lop_hanh_chinh (id "+id+",ma VARCHAR(20) NOT NULL UNIQUE,ten VARCHAR(150) NOT NULL,"+
                "nganh_id "+reference+" NOT NULL,khoa_hoc_id "+reference+" NOT NULL,FOREIGN KEY(nganh_id) REFERENCES nganh(id),"+
                "FOREIGN KEY(khoa_hoc_id) REFERENCES khoa_hoc(id))"+suffix);
        db.execute("CREATE TABLE IF NOT EXISTS chuong_trinh (id "+id+",ma VARCHAR(20) NOT NULL UNIQUE,ten VARCHAR(150) NOT NULL,"+
                "nganh_id "+reference+" NOT NULL,so_tin_chi INTEGER NOT NULL CHECK(so_tin_chi BETWEEN 1 AND 500),"+
                "FOREIGN KEY(nganh_id) REFERENCES nganh(id))"+suffix);
        db.execute("CREATE TABLE IF NOT EXISTS ho_so_sinh_vien (sinh_vien_id "+reference+" PRIMARY KEY,lop_hanh_chinh_id "+reference+
                " NOT NULL,ngay_sinh VARCHAR(10),gioi_tinh VARCHAR(10),dien_thoai VARCHAR(25),trang_thai VARCHAR(20) NOT NULL DEFAULT 'DANG_HOC',"+
                "chuong_trinh_tot_nghiep_id "+reference+",FOREIGN KEY(chuong_trinh_tot_nghiep_id) REFERENCES chuong_trinh(id),"+
                "FOREIGN KEY(sinh_vien_id) REFERENCES nguoi_dung(id),FOREIGN KEY(lop_hanh_chinh_id) REFERENCES lop_hanh_chinh(id),"+
                "CHECK(gioi_tinh IS NULL OR gioi_tinh IN ('NAM','NU','KHAC')),"+
                "CHECK(trang_thai IN ('DANG_HOC','BAO_LUU','THOI_HOC','TOT_NGHIEP')))"+suffix);
        db.execute("CREATE TABLE IF NOT EXISTS chuong_trinh_mon (id "+id+",chuong_trinh_id "+reference+" NOT NULL,mon_hoc_id "+reference+
                " NOT NULL,hoc_ky_du_kien INTEGER NOT NULL CHECK(hoc_ky_du_kien BETWEEN 1 AND 20),bat_buoc INTEGER NOT NULL CHECK(bat_buoc IN (0,1)),"+
                "FOREIGN KEY(chuong_trinh_id) REFERENCES chuong_trinh(id),FOREIGN KEY(mon_hoc_id) REFERENCES mon_hoc(id),UNIQUE(chuong_trinh_id,mon_hoc_id))"+suffix);
    }
}
