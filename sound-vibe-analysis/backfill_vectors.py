"""
一次性脚本：为所有缺少 audio_vector 的音频资产补充 CLAP 嵌入向量
在 Docker 容器内运行: python backfill_vectors.py
"""
import json
import logging
import os
import sys
import tempfile

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("backfill")

from src.config import db_config, minio_config
from src.database import engine
from src.minio_client import download_to_temp
from src.model_manager import get_manager

from sqlalchemy import text
from sqlalchemy.orm import Session, sessionmaker

SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)


def main():
    logger.info("开始补充音频嵌入向量...")

    manager = get_manager()
    logger.info("CLAP 模型已加载")

    with SessionLocal() as session:
        rows = session.execute(
            text("SELECT id, storage_name FROM assets WHERE deleted=0 AND type='AUDIO' AND audio_vector IS NULL")
        ).fetchall()

        logger.info("找到 %d 个缺少向量的音频资产", len(rows))

        for asset_id, storage_name in rows:
            try:
                logger.info("处理 asset_id=%d, storage_name=%s", asset_id, storage_name)
                temp_path = download_to_temp(storage_name)

                vector = manager.get_audio_embedding(temp_path)
                vector_json = json.dumps(vector)

                session.execute(
                    text("UPDATE assets SET audio_vector = :vec WHERE id = :id"),
                    {"vec": vector_json, "id": asset_id},
                )
                session.commit()

                logger.info("✅ asset_id=%d 向量已更新 (dim=%d)", asset_id, len(vector))

                os.remove(temp_path)
            except Exception:
                logger.exception("❌ asset_id=%d 处理失败", asset_id)
                session.rollback()

    logger.info("补充完成!")


if __name__ == "__main__":
    main()
