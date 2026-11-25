-- MySQL 8.0.16+ / MariaDB 10.6+. Run in the selected application database in phpMyAdmin.
-- All tables use InnoDB and real foreign keys. No DROP or destructive reset.
CREATE TABLE IF NOT EXISTS nguoi_dung (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 ma_so VARCHAR(20) NOT NULL UNIQUE,ho_ten VARCHAR(100) NOT NULL,email VARCHAR(100) UNIQUE,
 mat_khau VARCHAR(100) NOT NULL,vai_tro VARCHAR(20) NOT NULL,
 bi_khoa INT NOT NULL DEFAULT 0,ban_phien INT NOT NULL DEFAULT 0,
 CHECK(vai_tro IN ('ADMIN','GIANG_VIEN','SINH_VIEN')),CHECK(bi_khoa IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS mon_hoc (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,ma_mon VARCHAR(20) NOT NULL UNIQUE,ten_mon VARCHAR(100) NOT NULL,
 so_tin_chi INT NOT NULL,mon_tien_quyet_id BIGINT,
 ty_le_chuyen_can INT NOT NULL DEFAULT 10,ty_le_qua_trinh INT NOT NULL DEFAULT 30,ty_le_thi INT NOT NULL DEFAULT 60,
 FOREIGN KEY(mon_tien_quyet_id) REFERENCES mon_hoc(id),
 CHECK(so_tin_chi BETWEEN 1 AND 30),
 CHECK(ty_le_chuyen_can BETWEEN 0 AND 100 AND ty_le_qua_trinh BETWEEN 0 AND 100 AND ty_le_thi BETWEEN 0 AND 100
 AND ty_le_chuyen_can+ty_le_qua_trinh+ty_le_thi=100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS lop_hoc (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,ma_lop VARCHAR(20) NOT NULL UNIQUE,mon_hoc_id BIGINT NOT NULL,
 giang_vien_id BIGINT NOT NULL,hoc_ky VARCHAR(6) NOT NULL,si_so_toi_da INT NOT NULL DEFAULT 60,da_chot_diem INT NOT NULL DEFAULT 0,
 FOREIGN KEY(mon_hoc_id) REFERENCES mon_hoc(id),FOREIGN KEY(giang_vien_id) REFERENCES nguoi_dung(id),
 CHECK(si_so_toi_da BETWEEN 1 AND 500),CHECK(da_chot_diem IN (0,1)),
 INDEX idx_lop_hoc_term(hoc_ky,giang_vien_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS dang_ky (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,lop_hoc_id BIGINT NOT NULL,sinh_vien_id BIGINT NOT NULL,
 ngay_dang_ky VARCHAR(10),diem_qua_trinh DECIMAL(4,2),diem_thi DECIMAL(4,2),
 diem_tong_ket DECIMAL(4,2),diem_chu VARCHAR(2),bi_cam_thi INT NOT NULL DEFAULT 0,
 FOREIGN KEY(lop_hoc_id) REFERENCES lop_hoc(id),FOREIGN KEY(sinh_vien_id) REFERENCES nguoi_dung(id),
 UNIQUE(lop_hoc_id,sinh_vien_id),INDEX idx_dang_ky_sinh_vien(sinh_vien_id,lop_hoc_id),
 CHECK(diem_qua_trinh BETWEEN 0 AND 10),CHECK(diem_thi BETWEEN 0 AND 10),CHECK(diem_tong_ket BETWEEN 0 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS buoi_hoc (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,lop_hoc_id BIGINT NOT NULL,ngay_hoc VARCHAR(10) NOT NULL,
 gio_bat_dau VARCHAR(8) NOT NULL,gio_ket_thuc VARCHAR(8) NOT NULL,phong VARCHAR(20),
 ma_diem_danh VARCHAR(8),han_diem_danh BIGINT,noi_dung VARCHAR(255),qr_secret VARCHAR(64),
 FOREIGN KEY(lop_hoc_id) REFERENCES lop_hoc(id),CHECK(gio_ket_thuc>gio_bat_dau),
 INDEX idx_buoi_hoc_lop(lop_hoc_id,ngay_hoc),INDEX idx_buoi_hoc_room(ngay_hoc,phong)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS diem_danh (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,buoi_hoc_id BIGINT NOT NULL,sinh_vien_id BIGINT NOT NULL,
 trang_thai VARCHAR(12) NOT NULL,thoi_gian BIGINT,ghi_chu VARCHAR(255),device_hash VARCHAR(64),
 FOREIGN KEY(buoi_hoc_id) REFERENCES buoi_hoc(id),FOREIGN KEY(sinh_vien_id) REFERENCES nguoi_dung(id),
 UNIQUE(buoi_hoc_id,sinh_vien_id),UNIQUE KEY idx_diem_danh_device(buoi_hoc_id,device_hash),
 CHECK(trang_thai IN ('CO_MAT','DI_MUON','VANG','CO_PHEP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS hoc_ky (
 ma VARCHAR(6) PRIMARY KEY,mo_dang_ky VARCHAR(10) NOT NULL,dong_dang_ky VARCHAR(10) NOT NULL,nguong_chuyen_can INT NOT NULL,
 CHECK(nguong_chuyen_can BETWEEN 0 AND 100),CHECK(dong_dang_ky>=mo_dang_ky)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS don_xin_phep (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,buoi_hoc_id BIGINT NOT NULL,sinh_vien_id BIGINT NOT NULL,
 ly_do VARCHAR(500) NOT NULL,minh_chung VARCHAR(512),trang_thai VARCHAR(12) NOT NULL,nguoi_duyet_id BIGINT,
 FOREIGN KEY(buoi_hoc_id) REFERENCES buoi_hoc(id),FOREIGN KEY(sinh_vien_id) REFERENCES nguoi_dung(id),
 FOREIGN KEY(nguoi_duyet_id) REFERENCES nguoi_dung(id),UNIQUE(buoi_hoc_id,sinh_vien_id),
 CHECK(trang_thai IN ('CHO_DUYET','DA_DUYET','TU_CHOI'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS minh_chung (
 id VARCHAR(36) PRIMARY KEY,don_id BIGINT NOT NULL UNIQUE,loai VARCHAR(64) NOT NULL,noi_dung MEDIUMBLOB NOT NULL,
 FOREIGN KEY(don_id) REFERENCES don_xin_phep(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS nhat_ky_diem (
 id VARCHAR(36) PRIMARY KEY,lop_hoc_id BIGINT NOT NULL,nguoi_dung_id BIGINT NOT NULL,
 hanh_dong VARCHAR(20) NOT NULL,ly_do VARCHAR(500) NOT NULL,thoi_gian BIGINT NOT NULL,
 FOREIGN KEY(lop_hoc_id) REFERENCES lop_hoc(id),FOREIGN KEY(nguoi_dung_id) REFERENCES nguoi_dung(id),
 INDEX idx_nhat_ky_diem(lop_hoc_id,thoi_gian)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
