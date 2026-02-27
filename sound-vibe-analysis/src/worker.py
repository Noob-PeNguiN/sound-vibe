"""
SoundVibe Analysis Worker - RabbitMQ 消费者
监听 asset.uploaded 事件，执行音频分析流程

处理流程:
  1. 接收 MQ 消息 → 解析 JSON
  2. 从 MinIO 下载音频到临时目录
  3. 调用 processor.analyze_audio() 提取 BPM/调性/时长
  4. 调用 model_manager.get_audio_embedding() 生成 512 维嵌入向量
  5. 调用 tagger.match_tags() 执行 Zero-Shot 自动标注
  6. 更新 MySQL assets 表（含 audio_vector + auto_tags）
  7. 清理临时文件
  8. ACK 消息

约束:
  - prefetch_count=1: 一次只处理一个文件，防止 OOM
  - 分析失败时仍然 ACK（避免消息无限重试），并标记 status=4
  - 嵌入向量生成失败不阻断 BPM/调性分析，仅记录警告
"""

from __future__ import annotations

import json
import logging
import os
import time
from typing import Optional

import pika
from pika.adapters.blocking_connection import BlockingChannel
from pika.spec import Basic, BasicProperties

from src.config import rabbitmq_config
from src.database import mark_asset_analysis_failed, update_asset_analysis
from src.minio_client import download_to_temp
from src.model_manager import get_manager
from src.processor import analyze_audio
from src.tagger import match_tags

logger = logging.getLogger(__name__)


def _on_message(
    channel: BlockingChannel,
    method: Basic.Deliver,
    properties: BasicProperties,
    body: bytes,
) -> None:
    """
    消息回调处理函数

    :param channel: AMQP 通道
    :param method: 投递元信息（含 delivery_tag）
    :param properties: 消息属性
    :param body: 消息体（JSON bytes）
    """
    temp_path: Optional[str] = None
    asset_id: Optional[int] = None

    try:
        # 1. 解析消息
        payload = json.loads(body.decode("utf-8"))
        asset_id = payload.get("assetId")
        storage_name = payload.get("storageName")

        if not asset_id or not storage_name:
            logger.error("消息格式无效，缺少 assetId 或 storageName: %s", payload)
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return

        logger.info(
            "收到分析任务: asset_id=%s, storage_name=%s",
            asset_id, storage_name,
        )

        # 2. 从 MinIO 下载文件
        start_time = time.time()
        temp_path = download_to_temp(storage_name)
        download_elapsed = time.time() - start_time
        logger.info("文件下载耗时: %.2fs", download_elapsed)

        # 3. 执行音频分析 (BPM / 调性 / 时长)
        start_time = time.time()
        result = analyze_audio(temp_path)
        analysis_elapsed = time.time() - start_time
        logger.info("音频分析耗时: %.2fs", analysis_elapsed)

        # 4. 生成音频嵌入向量 (CLAP 512 维)
        audio_vector: Optional[list[float]] = None
        try:
            start_time = time.time()
            manager = get_manager()
            audio_vector = manager.get_audio_embedding(temp_path)
            embed_elapsed = time.time() - start_time
            logger.info("嵌入向量生成耗时: %.2fs (dim=%d)", embed_elapsed, len(audio_vector))
        except Exception:
            logger.exception("嵌入向量生成失败，跳过 (不影响 BPM/Key 分析): asset_id=%s", asset_id)

        # 5. Zero-Shot 自动标注
        auto_tags: Optional[str] = None
        if audio_vector:
            try:
                start_time = time.time()
                auto_tags = match_tags(audio_vector)
                tag_elapsed = time.time() - start_time
                logger.info("自动标注耗时: %.4fs, tags=%s", tag_elapsed, auto_tags)
            except Exception:
                logger.exception("自动标注失败，跳过: asset_id=%s", asset_id)

        # 6. 更新数据库
        update_asset_analysis(
            asset_id=asset_id,
            bpm=result["bpm"],
            musical_key=result["key"],
            duration=result["duration"],
            audio_vector=audio_vector,
            auto_tags=auto_tags,
        )

        logger.info(
            "✅ 分析任务完成: asset_id=%s, bpm=%d, key=%s, duration=%ds, vector=%s, tags=%s "
            "(下载 %.1fs + 分析 %.1fs)",
            asset_id, result["bpm"], result["key"], result["duration"],
            f"dim={len(audio_vector)}" if audio_vector else "None",
            auto_tags or "None",
            download_elapsed, analysis_elapsed,
        )

        # 7. 发送分析完成通知，触发 vibe-catalog 重新同步 ES 索引
        try:
            completion_msg = json.dumps({"assetId": asset_id})
            channel.basic_publish(
                exchange=rabbitmq_config.exchange,
                routing_key="asset.analysis.completed",
                body=completion_msg,
                properties=pika.BasicProperties(
                    content_type="application/json",
                    delivery_mode=2,
                ),
            )
            logger.info("分析完成通知已发送: asset_id=%s", asset_id)
        except Exception:
            logger.exception("分析完成通知发送失败（不影响分析结果）: asset_id=%s", asset_id)

    except Exception:
        logger.exception("❌ 分析任务失败: asset_id=%s", asset_id)
        # 标记资产分析失败
        if asset_id is not None:
            try:
                mark_asset_analysis_failed(asset_id)
            except Exception:
                logger.exception("标记分析失败状态时出错: asset_id=%s", asset_id)

    finally:
        # 5. 清理临时文件
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
                logger.debug("临时文件已清理: %s", temp_path)
            except OSError as e:
                logger.warning("临时文件清理失败: %s, error=%s", temp_path, e)

        # 6. 始终 ACK 消息（防止无限重试）
        channel.basic_ack(delivery_tag=method.delivery_tag)


def _create_connection() -> pika.BlockingConnection:
    """创建 RabbitMQ 连接"""
    credentials = pika.PlainCredentials(
        username=rabbitmq_config.user,
        password=rabbitmq_config.password,
    )
    parameters = pika.ConnectionParameters(
        host=rabbitmq_config.host,
        port=rabbitmq_config.port,
        credentials=credentials,
        heartbeat=600,
        blocked_connection_timeout=300,
    )
    return pika.BlockingConnection(parameters)


def start_worker() -> None:
    """
    启动 RabbitMQ 消费者

    包含自动重连机制:
      - 连接断开后等待 5 秒重试
      - 无限重试直到成功连接
    """
    logger.info("🚀 Audio Analysis Worker 启动中...")
    logger.info(
        "  RabbitMQ: %s:%d, Exchange: %s, Queue: %s",
        rabbitmq_config.host,
        rabbitmq_config.port,
        rabbitmq_config.exchange,
        rabbitmq_config.queue,
    )

    while True:
        try:
            connection = _create_connection()
            channel = connection.channel()

            # 声明 Exchange 和 Queue（幂等操作，确保存在）
            channel.exchange_declare(
                exchange=rabbitmq_config.exchange,
                exchange_type="topic",
                durable=True,
            )
            channel.queue_declare(
                queue=rabbitmq_config.queue,
                durable=True,
            )
            channel.queue_bind(
                queue=rabbitmq_config.queue,
                exchange=rabbitmq_config.exchange,
                routing_key=rabbitmq_config.routing_key,
            )

            # 每次只处理一个消息（CPU 密集型任务，防止 OOM）
            channel.basic_qos(prefetch_count=1)

            # 注册消费回调
            channel.basic_consume(
                queue=rabbitmq_config.queue,
                on_message_callback=_on_message,
                auto_ack=False,
            )

            logger.info("✅ Worker 已连接，开始监听消息...")
            channel.start_consuming()

        except pika.exceptions.AMQPConnectionError as e:
            logger.error("RabbitMQ 连接失败: %s, 5 秒后重试...", e)
            time.sleep(5)

        except KeyboardInterrupt:
            logger.info("Worker 收到中断信号，正在关闭...")
            break

        except Exception:
            logger.exception("Worker 异常退出，5 秒后重试...")
            time.sleep(5)
