"""
SoundVibe 生成资源上传模块
将生成的音频文件上传至 MinIO 并返回可访问 URL
"""

import logging
import os
import uuid
from datetime import timedelta

from minio import Minio

from src.config import minio_config

logger = logging.getLogger(__name__)

# 内部客户端：用于容器间上传/下载（endpoint 可能是 Docker 内部地址如 vibe-minio:9000）
_client = Minio(
    endpoint=minio_config.endpoint,
    access_key=minio_config.access_key,
    secret_key=minio_config.secret_key,
    secure=minio_config.secure,
)

# 外部客户端：用于生成浏览器可访问的预签名 URL（endpoint 是宿主机可达地址如 localhost:9000）
# 预设 region 避免 _get_region() 发起网络请求（外部 endpoint 在容器内不可达）
_external_endpoint = minio_config.external_endpoint or minio_config.endpoint
_url_client = Minio(
    endpoint=_external_endpoint,
    access_key=minio_config.access_key,
    secret_key=minio_config.secret_key,
    secure=minio_config.secure,
    region="us-east-1",
) if _external_endpoint != minio_config.endpoint else _client

_UPLOAD_PREFIX = "samples"
_URL_EXPIRY = timedelta(hours=24)

logger.info(
    "MinIO 客户端初始化: internal=%s, external=%s",
    minio_config.endpoint, _external_endpoint,
)


def _ensure_bucket() -> None:
    if not _client.bucket_exists(minio_config.bucket):
        _client.make_bucket(minio_config.bucket)
        logger.info("MinIO bucket 已创建: %s", minio_config.bucket)


def upload_sample(file_path: str) -> str:
    """
    将本地 .wav 文件上传至 MinIO 并返回预签名 URL

    :param file_path: 本地 .wav 文件路径
    :return: 预签名下载 URL（24 小时有效）
    """
    _ensure_bucket()

    object_name = f"{_UPLOAD_PREFIX}/{uuid.uuid4().hex}.wav"

    _client.fput_object(
        bucket_name=minio_config.bucket,
        object_name=object_name,
        file_path=file_path,
        content_type="audio/wav",
    )

    url = _url_client.presigned_get_object(
        bucket_name=minio_config.bucket,
        object_name=object_name,
        expires=_URL_EXPIRY,
    )

    logger.info("文件上传成功: object=%s, url=%s", object_name, url[:80])
    return url
