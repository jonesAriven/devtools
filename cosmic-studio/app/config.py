"""cosmic-studio 配置：全部走环境变量，容器内由 compose 注入。"""
import logging
import os

# ⚠️ 默认值一律置空：绝不回退到内网 IP / 弱口令，部署方必须用环境变量注入。
# 缺失时启动阶段报警（validate_config），fail-fast 比“静默连上开发库”更安全。
DB_HOST = os.getenv("DB_HOST", "")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_USER = os.getenv("DB_USER", "")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")

DB_ACTIVE = os.getenv("DB_ACTIVE", "cosmic_active")
DB_ARCHIVE = os.getenv("DB_ARCHIVE", "cosmic_archive")
DB_STUDIO = os.getenv("DB_STUDIO", "cosmic_studio")

DATA_DIR = os.getenv("DATA_DIR", "/data")
VERSIONS_DIR = os.path.join(DATA_DIR, "versions")
BACKUP_DIR = os.path.join(DATA_DIR, "backups")

DEFAULT_INITIATOR = "一线坐席"
DEFAULT_RECEIVER = "多媒体卡片平台"


def validate_config():
    """启动期校验必填项；缺失只报警不阻断（compose 已注入，本地 dev 未设会连不上 DB）。"""
    missing = [k for k, v in (("DB_HOST", DB_HOST), ("DB_USER", DB_USER),
                              ("DB_PASSWORD", DB_PASSWORD)) if not v]
    if missing:
        logging.warning("cosmic-studio 配置缺失：%s 未设置，请用环境变量注入，否则无法连接数据库",
                        ", ".join(missing))
