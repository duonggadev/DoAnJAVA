"""Verify that the MySQL application database faithfully holds the SQLite backup.

Run after scripts/migrate-sqlite-to-mysql.py. The SQLite file remains the rollback
backup; this script checks row counts, preserved ids/passwords and referential
integrity on the MySQL side.
"""
import os
import sqlite3
from pathlib import Path
from urllib.parse import urlparse

import pymysql

root = Path(__file__).resolve().parents[1]
source = root / 'backend/data/so-lop.db'

raw = os.environ.get('SOLOP_DB_URL', 'jdbc:mysql://127.0.0.1:3306/solop_enterprise')
url = urlparse(raw.removeprefix('jdbc:'))
target = pymysql.connect(
    host=url.hostname or '127.0.0.1',
    port=url.port or 3306,
    database=(url.path or '/solop_enterprise').lstrip('/'),
    user=os.environ.get('SOLOP_DB_USER', 'solop_app'),
    password=os.environ.get('SOLOP_DB_PASSWORD', 'Solop2026!'),
    charset='utf8mb4',
)
src = sqlite3.connect(f'file:{source.resolve()}?mode=ro', uri=True)
cur = target.cursor()

tables = [row[0] for row in src.execute(
    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
for table in tables:
    expected = src.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0]
    cur.execute(f'SELECT COUNT(*) FROM `{table}`')
    actual = cur.fetchone()[0]
    assert expected == actual, f'{table}: sqlite={expected} mysql={actual}'

sqlite_users = [tuple(row) for row in src.execute(
    'SELECT id,ma_so,mat_khau,vai_tro FROM nguoi_dung ORDER BY id')]
cur.execute('SELECT id,ma_so,mat_khau,vai_tro FROM nguoi_dung ORDER BY id')
assert [tuple(row) for row in cur.fetchall()] == sqlite_users, 'user ids/passwords changed'

cur.execute('''SELECT COUNT(*) FROM dang_ky d LEFT JOIN nguoi_dung u ON u.id=d.sinh_vien_id
               LEFT JOIN lop_hoc l ON l.id=d.lop_hoc_id WHERE u.id IS NULL OR l.id IS NULL''')
assert cur.fetchone()[0] == 0, 'orphan enrollment rows'
cur.execute('''SELECT COUNT(*) FROM diem_danh d LEFT JOIN buoi_hoc b ON b.id=d.buoi_hoc_id
               LEFT JOIN nguoi_dung u ON u.id=d.sinh_vien_id WHERE b.id IS NULL OR u.id IS NULL''')
assert cur.fetchone()[0] == 0, 'orphan attendance rows'
cur.execute("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_type='FOREIGN KEY' AND table_schema=DATABASE()")
assert cur.fetchone()[0] > 0, 'no foreign keys in MySQL schema'

cur.close()
target.close()
src.close()
print(f'PASS MySQL migration: {len(tables)} tables preserved, ids and passwords intact, no orphan rows, foreign keys enforced')
