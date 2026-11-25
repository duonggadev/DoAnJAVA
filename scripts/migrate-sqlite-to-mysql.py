"""Migrate the legacy SQLite database into MySQL without changing ids or passwords.

Usage:
    python scripts/migrate-sqlite-to-mysql.py [--truncate] [--dry-run]

Connection settings come from SOLOP_DB_URL / SOLOP_DB_USER / SOLOP_DB_PASSWORD,
falling back to the local Laragon defaults used by backend/src/main/resources/application.properties.
The SQLite source stays untouched and is kept as a rollback backup.
"""
import argparse
import os
from pathlib import Path
from urllib.parse import urlparse

import pymysql
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "backend/data/so-lop.db"

# Parents before children so foreign keys are satisfied during insertion.
INSERT_ORDER = [
    "nguoi_dung", "mon_hoc", "lop_hoc", "buoi_hoc", "hoc_ky",
    "khoa", "nganh", "khoa_hoc", "lop_hanh_chinh", "chuong_trinh",
    "chuong_trinh_mon", "ho_so_sinh_vien", "dang_ky", "diem_danh",
    "don_xin_phep", "minh_chung", "nhat_ky_diem", "nhat_ky_he_thong",
    "thong_bao", "khoa_nghiep_vu",
]
TRUNCATE_ORDER = list(reversed(INSERT_ORDER))


def mysql_config():
    raw = os.environ.get("SOLOP_DB_URL", "jdbc:mysql://127.0.0.1:3306/solop_enterprise")
    url = urlparse(raw.removeprefix("jdbc:"))
    return dict(
        host=url.hostname or "127.0.0.1",
        port=url.port or 3306,
        database=(url.path or "/solop_enterprise").lstrip("/"),
        user=os.environ.get("SOLOP_DB_USER", "solop_app"),
        password=os.environ.get("SOLOP_DB_PASSWORD", "Solop2026!"),
        charset="utf8mb4",
        autocommit=False,
    )


def columns(cursor, table):
    cursor.execute(
        "SELECT column_name FROM information_schema.columns "
        "WHERE table_schema=DATABASE() AND table_name=%s ORDER BY ordinal_position",
        (table,),
    )
    return [row[0] for row in cursor.fetchall()]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--truncate", action="store_true", help="clear target tables before copying")
    parser.add_argument("--dry-run", action="store_true", help="only report what would be copied")
    args = parser.parse_args()

    if not SOURCE.exists():
        raise SystemExit(f"SQLite source not found: {SOURCE}")

    src = sqlite3.connect(f"file:{SOURCE.resolve()}?mode=ro", uri=True)
    src.row_factory = sqlite3.Row
    cfg = mysql_config()
    dst = pymysql.connect(**cfg)
    cur = dst.cursor()

    cur.execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")
    if cur.fetchone()[0] == 0:
        raise SystemExit("Target database has no tables; start the application once first.")

    cur.execute("SET FOREIGN_KEY_CHECKS=0")
    try:
        if args.truncate:
            for table in TRUNCATE_ORDER:
                cur.execute(f"TRUNCATE TABLE `{table}`")
        else:
            occupied = []
            for table in INSERT_ORDER:
                cur.execute(f"SELECT COUNT(*) FROM `{table}`")
                if cur.fetchone()[0]:
                    occupied.append(table)
            if occupied:
                raise SystemExit(
                    "Target tables already have data: " + ", ".join(occupied)
                    + ". Re-run with --truncate if you intend to overwrite them."
                )
        cur.execute("SET FOREIGN_KEY_CHECKS=1")

        counts = {}
        for table in INSERT_ORDER:
            rows = src.execute(f'SELECT * FROM "{table}"').fetchall()
            counts[table] = len(rows)
            if args.dry_run or not rows:
                continue
            src_cols = set(rows[0].keys())
            dst_cols = [c for c in columns(cur, table) if c in src_cols]
            placeholders = ",".join(["%s"] * len(dst_cols))
            col_sql = ",".join(f"`{c}`" for c in dst_cols)
            sql = f"INSERT INTO `{table}` ({col_sql}) VALUES ({placeholders})"
            cur.executemany(sql, [tuple(dict(row)[c] for c in dst_cols) for row in rows])

        dst.commit()
    except Exception:
        dst.rollback()
        raise
    finally:
        cur.execute("SET FOREIGN_KEY_CHECKS=1")
        cur.close()
        dst.close()
        src.close()

    print("Rows migrated:" if not args.dry_run else "Rows found (dry run):")
    for table, n in counts.items():
        print(f"  {table}: {n}")


if __name__ == "__main__":
    main()
