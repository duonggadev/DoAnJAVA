CREATE TABLE IF NOT EXISTS nguoi_dung (
  id INTEGER PRIMARY KEY,
  ma_so TEXT NOT NULL UNIQUE,
  ho_ten TEXT NOT NULL,
  email TEXT UNIQUE,
  mat_khau TEXT NOT NULL,
  vai_tro TEXT NOT NULL CHECK (vai_tro IN ('ADMIN','GIANG_VIEN','SINH_VIEN'))
);
CREATE TABLE IF NOT EXISTS mon_hoc (
  id INTEGER PRIMARY KEY,
  ma_mon TEXT NOT NULL UNIQUE,
  ten_mon TEXT NOT NULL,
  so_tin_chi INTEGER NOT NULL CHECK (so_tin_chi > 0)
);
CREATE TABLE IF NOT EXISTS lop_hoc (
  id INTEGER PRIMARY KEY,
  ma_lop TEXT NOT NULL UNIQUE,
  mon_hoc_id INTEGER NOT NULL REFERENCES mon_hoc(id),
  giang_vien_id INTEGER NOT NULL REFERENCES nguoi_dung(id),
  hoc_ky TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS dang_ky (
  id INTEGER PRIMARY KEY,
  lop_hoc_id INTEGER NOT NULL REFERENCES lop_hoc(id),
  sinh_vien_id INTEGER NOT NULL REFERENCES nguoi_dung(id),
  diem_qua_trinh DECIMAL(4,2) CHECK (diem_qua_trinh BETWEEN 0 AND 10),
  diem_thi DECIMAL(4,2) CHECK (diem_thi BETWEEN 0 AND 10),
  ngay_dang_ky TEXT,
  UNIQUE(lop_hoc_id, sinh_vien_id)
);
CREATE TABLE IF NOT EXISTS buoi_hoc (
  id INTEGER PRIMARY KEY,
  lop_hoc_id INTEGER NOT NULL REFERENCES lop_hoc(id),
  ngay_hoc TEXT NOT NULL,
  gio_bat_dau TEXT NOT NULL,
  gio_ket_thuc TEXT NOT NULL CHECK (gio_ket_thuc > gio_bat_dau),
  phong TEXT,
  ma_diem_danh TEXT,
  han_diem_danh INTEGER
);
CREATE TABLE IF NOT EXISTS diem_danh (
  id INTEGER PRIMARY KEY,
  buoi_hoc_id INTEGER NOT NULL REFERENCES buoi_hoc(id),
  sinh_vien_id INTEGER NOT NULL REFERENCES nguoi_dung(id),
  trang_thai TEXT NOT NULL CHECK (trang_thai IN ('CO_MAT','DI_MUON','VANG','CO_PHEP')),
  thoi_gian INTEGER,
  ghi_chu TEXT,
  UNIQUE(buoi_hoc_id, sinh_vien_id)
);
CREATE INDEX IF NOT EXISTS idx_buoi_hoc_lop ON buoi_hoc(lop_hoc_id, ngay_hoc);

CREATE TABLE IF NOT EXISTS hoc_ky (
  ma TEXT PRIMARY KEY,
  mo_dang_ky TEXT NOT NULL,
  dong_dang_ky TEXT NOT NULL,
  nguong_chuyen_can INTEGER NOT NULL CHECK (nguong_chuyen_can BETWEEN 0 AND 100),
  CHECK (dong_dang_ky >= mo_dang_ky)
);
CREATE TABLE IF NOT EXISTS don_xin_phep (
  id INTEGER PRIMARY KEY,
  buoi_hoc_id INTEGER NOT NULL REFERENCES buoi_hoc(id),
  sinh_vien_id INTEGER NOT NULL REFERENCES nguoi_dung(id),
  ly_do TEXT NOT NULL,
  minh_chung TEXT,
  trang_thai TEXT NOT NULL CHECK (trang_thai IN ('CHO_DUYET','DA_DUYET','TU_CHOI')),
  nguoi_duyet_id INTEGER REFERENCES nguoi_dung(id),
  UNIQUE(buoi_hoc_id,sinh_vien_id)
);
CREATE INDEX IF NOT EXISTS idx_dang_ky_sinh_vien ON dang_ky(sinh_vien_id,lop_hoc_id);
CREATE TABLE IF NOT EXISTS minh_chung (
 id TEXT PRIMARY KEY,
 don_id INTEGER NOT NULL UNIQUE REFERENCES don_xin_phep(id),
 loai TEXT NOT NULL,
 noi_dung BLOB NOT NULL
);
CREATE TABLE IF NOT EXISTS nhat_ky_diem (
 id TEXT PRIMARY KEY,
 lop_hoc_id INTEGER NOT NULL REFERENCES lop_hoc(id),
 nguoi_dung_id INTEGER NOT NULL REFERENCES nguoi_dung(id),
 hanh_dong TEXT NOT NULL,
 ly_do TEXT NOT NULL,
 thoi_gian BIGINT NOT NULL
);