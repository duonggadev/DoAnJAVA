"""Isolated MySQL database lifecycle for the browser verification scripts.

These scripts start the real application jar against a throwaway database so the
production database is never touched. Database names, credentials and the JDBC
URL are derived from the same environment variables as the application config.
"""
import os
from urllib.parse import urlparse

import pymysql


def settings():
    raw = os.environ.get("SOLOP_DB_URL", "jdbc:mysql://127.0.0.1:3306/solop_enterprise")
    url = urlparse(raw.removeprefix("jdbc:"))
    return dict(
        host=url.hostname or "127.0.0.1",
        port=url.port or 3306,
        user=os.environ.get("SOLOP_DB_USER", "solop_app"),
        password=os.environ.get("SOLOP_DB_PASSWORD", "Solop2026!"),
    )


def _admin():
    s = settings()
    return pymysql.connect(
        host=s["host"],
        port=s["port"],
        user=os.environ.get("SOLOP_DB_ADMIN_USER", "root"),
        password=os.environ.get("SOLOP_DB_ADMIN_PASSWORD", ""),
        charset="utf8mb4",
    )


def create(name):
    """Drop any stale database and create a clean one granted to the app user."""
    s = settings()
    with _admin() as conn:
        with conn.cursor() as cur:
            cur.execute(f"DROP DATABASE IF EXISTS `{name}`")
            cur.execute(f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
            for host in ("localhost", "127.0.0.1"):
                cur.execute(f"GRANT ALL PRIVILEGES ON `{name}`.* TO '{s['user']}'@'{host}'")
            cur.execute("FLUSH PRIVILEGES")


def drop(name):
    with _admin() as conn:
        with conn.cursor() as cur:
            cur.execute(f"DROP DATABASE IF EXISTS `{name}`")


def jdbc_url(name):
    s = settings()
    return (f"jdbc:mysql://{s['host']}:{s['port']}/{name}"
            "?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=Asia/Ho_Chi_Minh&useAffectedRows=false")


def app_args(name):
    """Command-line properties that point the app at a throwaway database."""
    s = settings()
    return [
        f"--spring.datasource.url={jdbc_url(name)}",
        f"--spring.datasource.username={s['user']}",
        f"--spring.datasource.password={s['password']}",
        "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "--spring.sql.init.schema-locations=classpath:schema-mysql.sql",
    ]
