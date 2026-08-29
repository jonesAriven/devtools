"""cosmic-studio 配置：全部走环境变量，容器内由 compose 注入。"""
import os

DB_HOST = os.getenv("DB_HOST", "192.168.31.105")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "cosmic")
DB_PASSWORD = os.getenv("DB_PASSWORD", "cosmic_2026")

DB_ACTIVE = os.getenv("DB_ACTIVE", "cosmic_active")
DB_ARCHIVE = os.getenv("DB_ARCHIVE", "cosmic_archive")
DB_STUDIO = os.getenv("DB_STUDIO", "cosmic_studio")

DATA_DIR = os.getenv("DATA_DIR", "/data")
VERSIONS_DIR = os.path.join(DATA_DIR, "versions")
BACKUP_DIR = os.path.join(DATA_DIR, "backups")

DEFAULT_INITIATOR = "一线坐席"
DEFAULT_RECEIVER = "多媒体卡片平台"
