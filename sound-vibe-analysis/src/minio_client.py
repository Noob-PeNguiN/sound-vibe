"""
SoundVibe Analysis MinIO 客户端模块
负责从 MinIO 下载音频文件到本地临时路径
"""

import logging
import os
import tempfile

from minio import Minio

from src.config import minio_config

logger = logging.getLogger(__name__)

# 创建 MinIO 客户端（全局复用）
_client = Minio(
    endpoint=minio_config.endpoint,
    access_key=minio_config.access_key,
    secret_key=minio_config.secret_key,
    secure=minio_config.secure,
)


def download_to_temp(storage_name: str) -> str:
    """
    从 MinIO 下载文件到本地临时目录

    :param storage_name: MinIO 对象名（如 audio/2026/02/uuid.mp3）
    :return: 本地临时文件路径
    :raises Exception: 下载失败时抛出
    """
    # 从 storage_name 中提取扩展名，保证 librosa 能识别格式
    _, ext = os.path.splitext(storage_name)
    if not ext:
        ext = ".wav"

    # 创建临时文件（不自动删除，由调用方清理）
    fd, temp_path = tempfile.mkstemp(suffix=ext, prefix="vibe_analysis_")
    os.close(fd)

    try:
        _client.fget_object(
            bucket_name=minio_config.bucket,
            object_name=storage_name,
            file_path=temp_path,
        )
        file_size = os.path.getsize(temp_path)
        logger.info(
            "文件下载成功: storage_name=%s, temp_path=%s, size=%d bytes",
            storage_name, temp_path, file_size,
        )
        return temp_path
    except Exception:
        # 下载失败时清理临时文件
        _cleanup_file(temp_path)
        raise


def _cleanup_file(file_path: str) -> None:
    """安全删除临时文件"""
    try:
        if file_path and os.path.exists(file_path):
            os.remove(file_path)
    except OSError as e:
        logger.warning("临时文件清理失败: path=%s, error=%s", file_path, e)
