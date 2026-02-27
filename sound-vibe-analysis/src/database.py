"""
SoundVibe Analysis 数据库模块
使用 SQLAlchemy 直接操作 assets 表，更新音频分析结果及嵌入向量
"""

import json
import logging
from contextlib import contextmanager
from typing import Generator, Optional

from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session, sessionmaker

from src.config import db_config

logger = logging.getLogger(__name__)

# 创建数据库引擎（连接池配置）
engine = create_engine(
    db_config.url,
    pool_size=5,
    max_overflow=10,
    pool_recycle=3600,
    echo=False,
)

SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)


@contextmanager
def get_session() -> Generator[Session, None, None]:
    """获取数据库会话（上下文管理器，自动提交/回滚）"""
    session = SessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def update_asset_analysis(
    asset_id: int,
    bpm: int,
    musical_key: str,
    duration: int,
    audio_vector: Optional[list[float]] = None,
    auto_tags: Optional[str] = None,
) -> None:
    """
    更新资产的音频分析结果（含嵌入向量 + 自动标签）

    :param asset_id: 资产数据库主键
    :param bpm: 节拍速度 (BPM)
    :param musical_key: 音乐调性 (如 'C Major')
    :param duration: 音频时长 (秒)
    :param audio_vector: 512 维音频嵌入向量 (可选)
    :param auto_tags: 自动标注的标签，逗号分隔 (可选)
    """
    vector_json = json.dumps(audio_vector) if audio_vector else None

    with get_session() as session:
        session.execute(
            text("""
                UPDATE assets
                SET bpm = :bpm,
                    musical_key = :musical_key,
                    duration = :duration,
                    audio_vector = :audio_vector,
                    auto_tags = :auto_tags,
                    status = 1
                WHERE id = :asset_id
                  AND deleted = 0
            """),
            {
                "bpm": bpm,
                "musical_key": musical_key,
                "duration": duration,
                "audio_vector": vector_json,
                "auto_tags": auto_tags,
                "asset_id": asset_id,
            },
        )
    logger.info(
        "资产分析结果已更新: asset_id=%d, bpm=%d, key=%s, duration=%ds, vector=%s, tags=%s",
        asset_id, bpm, musical_key, duration,
        f"dim={len(audio_vector)}" if audio_vector else "None",
        auto_tags or "None",
    )


def mark_asset_analysis_failed(asset_id: int) -> None:
    """
    标记资产分析失败（status = 4）

    :param asset_id: 资产数据库主键
    """
    with get_session() as session:
        session.execute(
            text("""
                UPDATE assets
                SET status = 4
                WHERE id = :asset_id
                  AND deleted = 0
            """),
            {"asset_id": asset_id},
        )
    logger.warning("资产标记为分析失败: asset_id=%d", asset_id)
