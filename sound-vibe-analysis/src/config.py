"""
SoundVibe Analysis 配置模块
从 .env 文件加载所有外部服务连接参数
"""

import os
from dataclasses import dataclass
from dotenv import load_dotenv

# 加载 .env 文件（优先级低于系统环境变量）
load_dotenv()


@dataclass(frozen=True)
class DatabaseConfig:
    """MySQL 数据库连接配置"""
    host: str = os.getenv("DB_HOST", "localhost")
    port: int = int(os.getenv("DB_PORT", "3306"))
    name: str = os.getenv("DB_NAME", "sound_vibe_db")
    user: str = os.getenv("DB_USER", "root")
    password: str = os.getenv("DB_PASSWORD", "root")

    @property
    def url(self) -> str:
        """SQLAlchemy 连接 URL"""
        return (
            f"mysql+pymysql://{self.user}:{self.password}"
            f"@{self.host}:{self.port}/{self.name}"
            f"?charset=utf8mb4"
        )


@dataclass(frozen=True)
class RabbitMQConfig:
    """RabbitMQ 连接配置"""
    host: str = os.getenv("RABBITMQ_HOST", "localhost")
    port: int = int(os.getenv("RABBITMQ_PORT", "5672"))
    user: str = os.getenv("RABBITMQ_USER", "guest")
    password: str = os.getenv("RABBITMQ_PASSWORD", "guest")

    # Exchange & Queue 定义（与 Java 端 RabbitMQConfig 保持一致）
    exchange: str = "soundvibe.asset.topic"
    queue: str = "soundvibe.asset.analysis.queue"
    routing_key: str = "asset.uploaded"


@dataclass(frozen=True)
class MinIOConfig:
    """MinIO 对象存储连接配置"""
    endpoint: str = os.getenv("MINIO_ENDPOINT", "localhost:9000")
    access_key: str = os.getenv("MINIO_ACCESS_KEY", "minioadmin")
    secret_key: str = os.getenv("MINIO_SECRET_KEY", "minioadmin")
    bucket: str = os.getenv("MINIO_BUCKET", "soundvibe-assets")
    secure: bool = os.getenv("MINIO_SECURE", "false").lower() == "true"
    external_endpoint: str = os.getenv("MINIO_EXTERNAL_ENDPOINT", "")


@dataclass(frozen=True)
class ServiceConfig:
    """服务自身配置"""
    host: str = os.getenv("SERVICE_HOST", "0.0.0.0")
    port: int = int(os.getenv("SERVICE_PORT", "8090"))


# 全局单例配置
db_config = DatabaseConfig()
rabbitmq_config = RabbitMQConfig()
minio_config = MinIOConfig()
service_config = ServiceConfig()
