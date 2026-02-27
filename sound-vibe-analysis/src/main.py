"""
SoundVibe Audio Intelligence Service - 入口文件
同时启动:
  1. FastAPI HTTP 服务（健康检查 + 嵌入向量 API + AI 音乐生成）（后台线程）
  2. RabbitMQ Worker 消费者（主线程阻塞）
"""

import asyncio
import logging
import os
import sys
import threading

import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from src.config import service_config
from src.model_manager import EMBEDDING_DIM, get_manager
from src.music_generator import get_generator
from src.storage import upload_sample
from src.tagger import precompute_tag_embeddings
from src.worker import start_worker

# ======================== 日志配置 ========================
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("vibe-analysis")

# ======================== FastAPI 健康检查 ========================
app = FastAPI(
    title="SoundVibe Audio Analysis",
    description="音频智能分析微服务 - 提取 BPM / 调性 / 时长 / 嵌入向量",
    version="2.0.0",
)


# ======================== 请求/响应模型 ========================
class TextEmbedRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=512, description="文本描述")


class EmbedResponse(BaseModel):
    vector: list[float] = Field(..., description=f"嵌入向量 ({EMBEDDING_DIM} 维)")
    dimension: int = Field(default=EMBEDDING_DIM, description="向量维度")


class GenerateRequest(BaseModel):
    prompt: str = Field(..., min_length=1, max_length=512, description="音乐描述文本")
    duration: int = Field(default=5, ge=1, le=30, description="生成时长（秒）")


class GenerateResponse(BaseModel):
    url: str = Field(..., description="生成音频的下载 URL")


@app.get("/health", tags=["监控"])
def health_check():
    """健康检查端点，供 Docker / K8s 探针使用"""
    return {"status": "UP", "service": "vibe-analysis"}


@app.get("/", tags=["监控"])
def root():
    """根路径，返回服务基本信息"""
    return {
        "service": "SoundVibe Audio Analysis",
        "version": "2.0.0",
        "status": "running",
        "embedding_dim": EMBEDDING_DIM,
    }


# ======================== 嵌入向量 API ========================
@app.post("/api/embed/text", response_model=EmbedResponse, tags=["嵌入向量"])
def embed_text(req: TextEmbedRequest):
    """
    文本转嵌入向量 (供 Java 后端调用)

    将自然语言描述 (如 "cinematic dark synthwave") 转为 512 维密集向量，
    用于 Elasticsearch 向量相似度检索。
    """
    try:
        manager = get_manager()
        vector = manager.get_text_embedding(req.text)
        return EmbedResponse(vector=vector, dimension=len(vector))
    except Exception as e:
        logger.exception("文本嵌入生成失败: text='%s'", req.text[:60])
        raise HTTPException(status_code=500, detail=f"嵌入生成失败: {e}") from e


# ======================== AI 音乐生成 API ========================
@app.post("/api/generate", response_model=GenerateResponse, tags=["AI 生成"])
async def generate_music(req: GenerateRequest):
    """
    AI 音乐生成端点

    根据文本描述使用 MusicGen 生成音频片段，上传至 MinIO 并返回下载 URL。
    生成过程在后台线程执行以避免阻塞事件循环。
    """
    temp_path: str | None = None
    try:
        generator = get_generator()
        temp_path = await asyncio.to_thread(
            generator.generate_audio, req.prompt, req.duration,
        )
        url = await asyncio.to_thread(upload_sample, temp_path)
        return GenerateResponse(url=url)
    except Exception as e:
        logger.exception("音乐生成失败: prompt='%s'", req.prompt[:60])
        raise HTTPException(status_code=500, detail=f"音乐生成失败: {e}") from e
    finally:
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                logger.warning("临时文件清理失败: %s", temp_path)


def _start_api_server() -> None:
    """在后台线程中启动 FastAPI HTTP 服务"""
    uvicorn.run(
        app,
        host=service_config.host,
        port=service_config.port,
        log_level="info",
    )


# ======================== 主入口 ========================
def main() -> None:
    """
    服务启动入口

    启动顺序:
      1. 后台线程: FastAPI HTTP 健康检查
      2. 主线程: RabbitMQ Worker（阻塞监听）
    """
    logger.info("=" * 50)
    logger.info("  SoundVibe Audio Analysis Service v2.0")
    logger.info("  Health Check: http://%s:%d/health", service_config.host, service_config.port)
    logger.info("  Text Embed:   POST http://%s:%d/api/embed/text", service_config.host, service_config.port)
    logger.info("  AI Generate:  POST http://%s:%d/api/generate", service_config.host, service_config.port)
    logger.info("=" * 50)

    # 预加载 CLAP 模型（避免首次请求延迟）
    logger.info("预加载 CLAP 模型...")
    get_manager()
    logger.info("CLAP 模型就绪")

    # 预加载 MusicGen 模型（避免首次请求延迟）
    logger.info("预加载 MusicGen 模型...")
    get_generator()
    logger.info("MusicGen 模型就绪")

    # 预计算候选标签嵌入（用于 Zero-Shot Audio Tagging）
    logger.info("预计算候选标签嵌入...")
    precompute_tag_embeddings()
    logger.info("候选标签嵌入就绪")

    # 启动 HTTP 服务（daemon 线程，主线程退出时自动终止）
    api_thread = threading.Thread(target=_start_api_server, daemon=True)
    api_thread.start()

    # 启动 MQ Worker（主线程阻塞）
    start_worker()


if __name__ == "__main__":
    main()
